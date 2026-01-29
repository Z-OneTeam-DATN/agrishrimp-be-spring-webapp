package com.zone.agri.common;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Getter
public class CommonProperties {
//        @Value("${ssc.hostname}")
//        private String hostname;

    @Value("${mnl.tmp-dir:}")
    private String tmpDir;

    public String createUserTmpDir() {
        String userTmpDir = createUserTmpDirString();
        new File(userTmpDir).mkdirs();
        return userTmpDir;
    }

//        public String createUserTmpDirHostThread() {
//            String userTmpDir =
//                    createUserTmpDirString() + getHostname() + "/" + Thread.currentThread().getName() + "/";
//            new File(userTmpDir).mkdirs();
//            return userTmpDir;
//        }

    private String createUserTmpDirString() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String datetime = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return tmpDir + date + "/" + AuthUtils.getUserDetail().getId() + "/" + datetime + "/";
    }
}

