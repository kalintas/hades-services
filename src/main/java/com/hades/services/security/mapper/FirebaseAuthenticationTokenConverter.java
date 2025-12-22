package com.hades.services.security.mapper;

import com.hades.services.model.User;
import com.hades.services.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FirebaseAuthenticationTokenConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String uid = jwt.getSubject();
        Optional<User> userOpt = userService.findByFirebaseUid(uid);

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (userOpt.isPresent()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userOpt.get().getRole().name()));
        }

        return new UsernamePasswordAuthenticationToken(uid, jwt.getTokenValue(), authorities);
    }
}
