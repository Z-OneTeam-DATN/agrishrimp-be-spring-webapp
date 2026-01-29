package com.zone.agri.security;


import com.zone.agri.dto.user.UserDetail;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CustomUserDetail extends User {
   private final UserDetail userDetail;

    public CustomUserDetail(String userName, String password, UserDetail userDetail) {
        super(userName, password, Collections.emptyList());
        this.userDetail = userDetail;
    }
}
