#[allow(non_snake_case)]
pub mod android {

    use jni::objects::{JClass, JString};
    use jni::sys::jint;
    use jni::JNIEnv;
    use minivtun::{config_socket_factory, cryptor, Client, Config, RndzConfig};

    use std::fs;
    use std::io::Read;
    use std::os::unix::io::AsRawFd;
    use std::os::unix::net::{UnixListener, UnixStream};
    use std::os::unix::prelude::RawFd;
    use std::path::Path;

    const CONTROL_PATH: &str = "minivtun.sock";

    /// # Safety
    #[no_mangle]
    pub unsafe extern "C" fn Java_com_github_optman_minivtun_Native_run(
        env: JNIEnv,
        _: JClass,
        tun_fd: jint,
        svr_addr: JString,
        rndz_svr_addr: JString,
        rndz_remote_id: JString,
        rndz_local_id: JString,
        local_ip_v4: JString,
        local_ip_v6: JString,
        secret: JString,
        cipher: JString,
    ) {
        android_logger::init_once(
            android_logger::Config::default().with_min_level(log::Level::Info),
        );

        if let Err(err) = run(
            tun_fd,
            env.get_string(svr_addr).unwrap().into(),
            env.get_string(rndz_svr_addr).unwrap().into(),
            env.get_string(rndz_remote_id).unwrap().into(),
            env.get_string(rndz_local_id).unwrap().into(),
            env.get_string(local_ip_v4).unwrap().into(),
            env.get_string(local_ip_v6).unwrap().into(),
            env.get_string(secret).unwrap().into(),
            env.get_string(cipher).unwrap().into(),
        ) {
            log::error!("run fail {:?}", err);
        };
    }

    /// # Safety
    #[no_mangle]
    pub unsafe extern "C" fn Java_com_github_optman_minivtun_Native_info<'a>(
        env: JNIEnv<'a>,
        _: JClass<'a>,
    ) -> JString<'a> {
        let mut buf = String::new();
        if let Ok(mut ctrl) = UnixStream::connect(Path::new(CONTROL_PATH)) {
            let _ = ctrl.read_to_string(&mut buf);
        }

        env.new_string(buf).unwrap()
    }

    pub(crate) fn run(
        tun: RawFd,
        svr_addr: String,
        rndz_svr_addr: String,
        rndz_remote_id: String,
        rndz_local_id: String,
        local_ip_v4: String,
        local_ip_v6: String,
        secret: String,
        cipher: String,
    ) -> Result<(), Box<dyn std::error::Error>> {
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

        let ctrl_path = Path::new(CONTROL_PATH);
        if ctrl_path.exists() {
            fs::remove_file(ctrl_path)?;
        }

        config.with_control_fd(UnixListener::bind(ctrl_path)?.as_raw_fd());

        Client::new(config)?.run()
    }
}

#[cfg(test)]
mod test {
    use super::android;
    use std::{
        fs::File,
        os::unix::prelude::{AsFd, AsRawFd},
        thread,
    };

    #[test]
    fn run_stop() {
        let null_dev = File::open("/dev/null").unwrap();
        let null_fd = null_dev.as_fd().as_raw_fd();
        let join_handle = thread::spawn(move || {
            let _ = android::run(
                null_fd,
                "localhost:1234".into(),
                "".into(),
                "".into(),
                "".into(),
                "1.1.1.1".parse().unwrap(),
                "::1".parse().unwrap(),
                "".into(),
                "".into(),
            );
        });

        drop(null_dev);

        join_handle.join().unwrap();
    }
}
