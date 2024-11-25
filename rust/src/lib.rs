#[allow(non_snake_case)]
mod android {

    mod jni {
        use jni::sys::jlong;
        use libc::{socketpair, AF_UNIX, SOCK_DGRAM};
        use std::os::fd::{FromRawFd, OwnedFd, RawFd};
        use std::os::unix::net::UnixDatagram;

        use super::native::*;
        use jni::objects::{JClass, JObject, JString};
        use jni::sys::jint;
        use jni::JNIEnv;
        use minivtun::Config;

        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_prepare<'a>(
            mut env: JNIEnv<'a>,
            _: JClass,
            params: JString,
        ) -> JObject<'a> {
            android_logger::init_once(
                android_logger::Config::default().with_max_level(log::LevelFilter::Info),
            );

            LAST_ERROR.write().unwrap().clear();

            let mut prepare = || -> Result<_, Box<dyn std::error::Error>> {
                let params: Params = serde_json::from_str(env.get_string(&params)?.to_str()?)?;
                prepare(params)
            };

            // prepare config
            let (mut config, socket) = if let Ok((config, socket)) = prepare().map_err(|err| {
                *LAST_ERROR.write().unwrap() = format!("{:?}", err);
            }) {
                (config, socket)
            } else {
                return JObject::null();
            };

            //make socket pair
            let mut fds = [0; 2];
            if socketpair(AF_UNIX, SOCK_DGRAM, 0, fds.as_mut_ptr()) != 0 {
                *LAST_ERROR.write().unwrap() = "Failed to create socket pair".to_string();
                return JObject::null();
            }
            let (socket_read, socket_write) = (fds[0], fds[1]);
            config.with_exit_signal(OwnedFd::from_raw_fd(socket_read));

            // Convert Config to a raw pointer and return it as jlong
            let config = Box::into_raw(Box::new(config)) as jlong;
            let exit_signal = Box::into_raw(Box::new(socket_write)) as jlong;

            env.new_object(
                "com/github/optman/minivtun/Client",
                "(JIJ)V",
                &[config.into(), socket.into(), exit_signal.into()],
            )
            .unwrap_or_else(|_| JObject::null())
        }

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_run(
            _env: JNIEnv,
            _: JClass,
            config_ptr: jlong,
            tun: jint,
        ) {
            let run = || -> Result<(), Box<dyn std::error::Error>> {
                // Convert jlong back to Config
                let config = Box::from_raw(config_ptr as *mut Config);
                run(*config, unsafe { OwnedFd::from_raw_fd(tun as RawFd) })
            };

            if let Err(err) = run() {
                *LAST_ERROR.write().unwrap() = format!("{:?}", err);
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
            socket.send(&[1]).unwrap();
        }

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_freeConfig(
            _env: JNIEnv,
            _: JClass,
            config_ptr: jlong,
        ) {
            if config_ptr != 0 {
                let _ = Box::from_raw(config_ptr as *mut Config);
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
                Err(_) => LAST_ERROR.read().unwrap().clone(),
            };

            env.new_string(result).unwrap()
        }
    }

    pub(crate) mod native {
        use minivtun::{config_socket_factory, cryptor, Client, Config, RndzConfig};

        use std::io::Read;

        #[cfg(target_os = "android")]
        use std::os::android::net::SocketAddrExt;

        use std::os::fd::OwnedFd;
        #[cfg(target_os = "linux")]
        use std::os::linux::net::SocketAddrExt;

        use once_cell::sync::Lazy;
        use serde::Deserialize;
        use std::os::unix::io::AsRawFd;
        use std::os::unix::net::{SocketAddr, UnixListener, UnixStream};
        use std::os::unix::prelude::RawFd;
        use std::sync::RwLock;

        pub(crate) const CONTROL_PATH: &str = "minivtun.sock";
        pub(crate) static LAST_ERROR: Lazy<RwLock<String>> = Lazy::new(Default::default);

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

        pub(crate) fn prepare<'a>(
            params: Params,
        ) -> Result<(Config<'a>, RawFd), Box<dyn std::error::Error>> {
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
                config.rndz = Some(RndzConfig {
                    server: Some(rndz_svr_addr),
                    remote_id: Some(rndz_remote_id),
                    local_id: Some(rndz_local_id),
                    svr_sk_builder: None,
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

            let raw_socket_factory = config_socket_factory(&config);
            let socket = raw_socket_factory(&config, false)?;
            let socket_fd = socket.as_raw_fd();
            config.with_socket(socket);
            config.rebind = false;

            let ctrl_fd =
                UnixListener::bind_addr(&SocketAddr::from_abstract_name(CONTROL_PATH).unwrap())?;

            config.with_control_fd(ctrl_fd);

            Ok((config, socket_fd))
        }

        pub(crate) fn run(
            mut config: Config,
            tun_fd: OwnedFd,
        ) -> Result<(), Box<dyn std::error::Error>> {
            config.with_tun_fd(tun_fd);
            Client::new(config)?.run()
        }

        pub(crate) fn info() -> Result<String, std::io::Error> {
            let mut ctrl =
                UnixStream::connect_addr(&SocketAddr::from_abstract_name(CONTROL_PATH).unwrap())?;
            let mut buf = String::new();
            ctrl.read_to_string(&mut buf)?;
            Ok(buf)
        }
    }
}

#[cfg(test)]
mod test {
    use super::android::native::{prepare, run, Params};
    use std::{
        fs::File,
        os::unix::prelude::{AsFd, AsRawFd},
        thread,
    };

    #[test]
    fn run_stop() {
        let null_dev = File::open("/dev/null").unwrap();
        let null_fd = null_dev.as_fd().as_raw_fd();
        let params = Params {
            ..Default::default()
        };
        let join_handle = thread::spawn(move || {
            let (config, _fd) = prepare(params).unwrap();
            run(config, null_fd).unwrap();
        });

        std::thread::sleep(std::time::Duration::from_secs(1));

        drop(null_dev);
        join_handle.join().unwrap();
    }
}
