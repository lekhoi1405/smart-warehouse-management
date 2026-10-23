
package com.lekhoi.smart_warehouse.config;

import com.lekhoi.smart_warehouse.service.AuthorityService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceCustom implements UserDetailsService {

    private final AuthorityService authorityService;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return new UserDetailsCustom(authorityService.load(username));
    }
}
