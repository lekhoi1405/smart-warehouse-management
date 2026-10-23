
package com.lekhoi.smart_warehouse.config;

import java.util.Collection;

import com.lekhoi.smart_warehouse.service.AuthorityService;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserDetailsCustom implements UserDetails {

    private final AuthorityService.Snapshot account;

    public UserDetailsCustom(AuthorityService.Snapshot account) {
        this.account = account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return account.authorities();
    }

    @Override
    public String getPassword() {
        return account.password();
    }

    @Override
    public String getUsername() {
        return account.username();
    }

    @Override
    public boolean isEnabled() {
        return account.status() == com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum.ACTIVE;
    }
}
