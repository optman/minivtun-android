#[allow(non_snake_case)]
mod android {

    mod jni {
        use super::native::*;
        use jni::objects::{JClass, JObject, JString};
        use jni::sys::jint;
        use jni::sys::jlong;
        use jni::JNIEnv;
        use libc::{socketpair, AF_UNIX, SOCK_DGRAM};
        use minivtun::{Config, RuntimeBuilder, SocketConfigure};
        use once_cell::sync::Lazy;
        use std::os::fd::{FromRawFd, OwnedFd, RawFd};

        #[cfg(target_os = "android")]
        use std::os::android::net::SocketAddrExt;

        #[cfg(target_os = "linux")]
        use std::os::linux::net::SocketAddrExt;

        use std::os::unix::net::SocketAddr;
        use std::os::unix::net::{UnixDatagram, UnixListener};
        use std::rc::Rc;
        use std::sync::RwLock;
        use std::time::Duration;

        pub(crate) const CONTROL_PATH: &str = "minivtun.sock";
        pub(crate) static LAST_ERROR: Lazy<RwLock<String>> = Lazy::new(Default::default);

        fn clear_error() {
            LAST_ERROR.write().unwrap().clear();
        }
        fn set_error(err: String) {
            *LAST_ERROR.write().unwrap() = err;
        }

        fn get_error() -> String {
            LAST_ERROR.read().unwrap().clone()
        }

        struct ProtectSocket {
            env_raw: *mut jni::sys::JNIEnv,
            vpn_service_raw: jni::sys::jobject,
        }

        impl SocketConfigure for ProtectSocket {
            fn config_socket(&self, sk: RawFd) -> Result<(), std::io::Error> {
                let mut env = unsafe { JNIEnv::from_raw(self.env_raw).unwrap() };
                let vpn_service = unsafe { JObject::from_raw(self.vpn_service_raw) };

                if !env
                    .call_method(vpn_service, "protect", "(I)Z", &[sk.into()])
                    .unwrap()
                    .z()
                    .unwrap()
                {
                    return Err(std::io::Error::other("protect socket failed".to_owned()));
                }

                Ok(())
            }
        }

        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_prepare<'a>(
            mut env: JNIEnv<'a>,
            _: JClass,
            params: JString,
        ) -> JObject<'a> {
            android_logger::init_once(
                android_logger::Config::default().with_max_level(log::LevelFilter::Info),
            );

            clear_error();

            let mut prepare = || -> Result<_, Box<dyn std::error::Error>> {
                let params: Params = serde_json::from_str(env.get_string(&params)?.to_str()?)?;
                prepare(params)
            };

            // prepare config
            let Ok(mut config) = prepare().inspect_err(|err| {
                set_error(format!("{:?}", err));
            }) else {
                return JObject::null();
            };

            config.rebind_timeout = Duration::from_secs(120);
            config.rebind = true;

            let config: Rc<Config> = config.into();
            let mut builder = RuntimeBuilder::new(config.clone());

            let bind_ctrl_fd =
                || UnixListener::bind_addr(&SocketAddr::from_abstract_name(CONTROL_PATH)?);
            let Ok(ctrl_fd) = bind_ctrl_fd().inspect_err(|e| {
                set_error(format!("Failed to bind control socket: {:?}", e));
            }) else {
                return JObject::null();
            };

            builder.with_control_fd(ctrl_fd);

            //make socket pair
            let mut fds = [0; 2];
            if socketpair(AF_UNIX, SOCK_DGRAM, 0, fds.as_mut_ptr()) != 0 {
                set_error("Failed to create socket pair".to_string());
                return JObject::null();
            }
            let (socket_read, socket_write) = (fds[0], fds[1]);
            builder.with_exit_signal(OwnedFd::from_raw_fd(socket_read));

            // Convert Config to a raw pointer and return it as jlong
            let context = Box::into_raw(Box::new((config, builder))) as jlong;
            let exit_signal = Box::into_raw(Box::new(socket_write)) as jlong;

            env.new_object(
                "com/github/optman/minivtun/Client",
                "(JJ)V",
                &[context.into(), exit_signal.into()],
            )
            .unwrap_or_else(|_| JObject::null())
        }

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_run(
            env: JNIEnv,
            _: JClass,
            vpn_service: JObject,
            context: jlong,
            tun: jint,
        ) {
            let context = Box::from_raw(context as *mut (Rc<Config>, RuntimeBuilder));
            let (config, mut builder) = *context;

            let tun_fd = unsafe { OwnedFd::from_raw_fd(tun as RawFd) };
            builder.with_tun_fd(tun_fd);

            let protect_socket = ProtectSocket {
                env_raw: env.get_raw(),
                vpn_service_raw: vpn_service.as_raw(),
            };

            builder.with_socket_configure(Box::new(protect_socket));

            let run = || -> Result<(), Box<dyn std::error::Error>> {
                let rt = builder.build()?;
                run(config, rt)
            };

            if let Err(err) = run() {
                set_error(format!("{:?}", err));
            }
        }
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_stop(
            _env: JNIEnv,
            _: JClass,
            exit_signal: jlong,
        ) {
            let exit_signal = Box::from_raw(exit_signal as *mut RawFd);
            let socket = UnixDatagram::from_raw_fd(*exit_signal);
            //ignore errors
            let _ = socket.send(&[1]);
        }

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_free(
            _env: JNIEnv,
            _: JClass,
            context: jlong,
        ) {
            if context != 0 {
                let _ = Box::from_raw(context as *mut (Rc<Config>, RuntimeBuilder));
            }
        }

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_info<'a>(
            env: JNIEnv<'a>,
            _: JClass<'a>,
        ) -> JString<'a> {
            let result = match info() {
                Ok(res) => res,
                Err(_) => get_error(),
            };

            env.new_string(result).unwrap()
        }
    }

    pub(crate) mod native {
        use minivtun::{config::rndz, cryptor, Client, Config, Runtime};

        use std::{io::Read, rc::Rc};

        #[cfg(target_os = "android")]
        use std::os::android::net::SocketAddrExt;

        #[cfg(target_os = "linux")]
        use std::os::linux::net::SocketAddrExt;

        use serde::Deserialize;
        use std::os::unix::net::{SocketAddr, UnixStream};

        #[derive(Default, Deserialize)]
        pub(crate) struct Params {
            pub(crate) svr_addr: String,
            pub(crate) rndz_svr_addr: String,
            pub(crate) rndz_remote_id: String,
            pub(crate) rndz_local_id: String,
            pub(crate) local_ip_v4: String,
            pub(crate) local_ip_v6: String,
            pub(crate) secret: String,
            pub(crate) cipher: String,
        }

        pub(crate) fn prepare(params: Params) -> Result<Config, Box<dyn std::error::Error>> {
            let Params {
                svr_addr,
                rndz_svr_addr,
                rndz_remote_id,
                rndz_local_id,
                local_ip_v4,
                local_ip_v6,
                secret,
                cipher,
                ..
            } = params;
            log::info!(
                "svr_addr {}, local_ip_v4 {}, local_ip_v6 {}",
                svr_addr,
                local_ip_v4,
                local_ip_v6
            );

            let mut config = Config::new();

            if !svr_addr.is_empty() {
                config.with_server_addr(svr_addr);
            }

            if !local_ip_v4.is_empty() {
                config.with_ip_addr(local_ip_v4.parse()?);
            }

            if !local_ip_v6.is_empty() {
                config.with_ip_addr(local_ip_v6.parse()?);
            }

            if !rndz_svr_addr.is_empty() {
                config.rndz = Some(rndz::Config {
                    server: rndz_svr_addr,
                    local_id: rndz_local_id,
                    remote_id: Some(rndz_remote_id),
                })
            };

            if !secret.is_empty() {
                let cipher = if !cipher.is_empty() {
                    cipher
                } else {
                    "aes-128".into()
                };

                config.with_cryptor(cryptor::Builder::new(secret, cipher)?.build());
            }

            Ok(config)
        }

        pub(crate) fn run(
            config: Rc<Config>,
            rt: Runtime,
        ) -> Result<(), Box<dyn std::error::Error>> {
            Client::new(config, rt)?.run()
        }

        pub(crate) fn info() -> Result<String, std::io::Error> {
            let mut ctrl = UnixStream::connect_addr(
                &SocketAddr::from_abstract_name(super::jni::CONTROL_PATH).unwrap(),
            )?;
            let mut buf = String::new();
            ctrl.read_to_string(&mut buf)?;
            Ok(buf)
        }
    }
}

#[cfg(test)]
mod test {
    use super::android::native::{prepare, run, Params};
    use minivtun::{Config, RuntimeBuilder};
    use std::rc::Rc;
    use std::{fs::File, thread};

    #[test]
    fn run_stop() {
        let null_dev = File::open("/dev/null").unwrap();
        let params = Params {
            ..Default::default()
        };
        let join_handle = thread::spawn(move || {
            let config = prepare(params).unwrap();
            let config: Rc<Config> = config.into();
            let mut builder = RuntimeBuilder::new(config.clone());
            builder.with_tun_fd(null_dev.into());
            let rt = builder.build().unwrap();
            run(config, rt).unwrap();
        });

        std::thread::sleep(std::time::Duration::from_secs(1));

        join_handle.join().unwrap();
    }
}
