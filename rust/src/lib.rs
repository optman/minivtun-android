#![feature(tcp_quickack)]
#[allow(non_snake_case)]
mod android {

    mod jni {
        use super::native::*;
        use jni::objects::{JClass, JString};
        use jni::JNIEnv;

        /// # Safety
        #[no_mangle]
        unsafe extern "C" fn Java_com_github_optman_minivtun_Native_run(
            env: JNIEnv,
            _: JClass,
            params: JString,
        ) {
            android_logger::init_once(
                android_logger::Config::default().with_min_level(log::Level::Info),
            );

            LAST_ERROR.write().unwrap().clear();

            let run = || -> Result<(), Box<dyn std::error::Error>> {
                let params: Params = serde_json::from_str(env.get_string(params)?.to_str()?)?;
                run(params)
            };

            if let Err(err) = run() {
                *LAST_ERROR.write().unwrap() = format!("{:?}", err);
            };
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
            pub(crate) tun: RawFd,
            pub(crate) svr_addr: String,
            pub(crate) rndz_svr_addr: String,
            pub(crate) rndz_remote_id: String,
            pub(crate) rndz_local_id: String,
            pub(crate) local_ip_v4: String,
            pub(crate) local_ip_v6: String,
            pub(crate) secret: String,
            pub(crate) cipher: String,
        }

        pub(crate) fn run(params: Params) -> Result<(), Box<dyn std::error::Error>> {
            let Params {
                tun,
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
            config.with_tun_fd(tun);

            if !svr_addr.is_empty() {
                config.with_server_addr(svr_addr);
            }

            if !local_ip_v4.is_empty() {
                config.with_ip_addr(local_ip_v4.parse()?);
            }

            if !local_ip_v6.is_empty() {
                config.with_ip_addr(local_ip_v6.parse()?);
            }

            config.rebind = true;

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

            let raw_socket_factory = config_socket_factory(&mut config);
            config.with_socket_factory(&raw_socket_factory);

            let ctrl_fd =
                UnixListener::bind_addr(&SocketAddr::from_abstract_name(CONTROL_PATH).unwrap())?;

            config.with_control_fd(ctrl_fd.as_raw_fd());

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
    use super::android::native::{run, Params};
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
            tun: null_fd,
            ..Default::default()
        };
        let join_handle = thread::spawn(move || {
            if let Err(_e) = run(params) {
                //println!("{:?}", e);
            }
        });

        std::thread::sleep(std::time::Duration::from_secs(1));

        drop(null_dev);
        join_handle.join().unwrap();
    }
}
