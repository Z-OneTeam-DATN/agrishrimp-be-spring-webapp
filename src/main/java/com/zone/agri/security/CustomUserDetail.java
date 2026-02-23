package com.zone.agri.security;

import com.zone.agri.dto.user.UserDetail;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CustomUserDetail extends User {

    private final UserDetail userDetail;

    public CustomUserDetail(
            String userName,
            String password,
            boolean enabled,
            boolean accountNonLocked,
            UserDetail userDetail,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(userName, password, enabled, true, true, accountNonLocked, authorities);
        this.userDetail = userDetail;
    }
}
