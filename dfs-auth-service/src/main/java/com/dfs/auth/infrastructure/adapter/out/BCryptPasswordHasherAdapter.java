package com.dfs.auth.infrastructure.adapter.out;

import com.dfs.auth.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Wraps Spring Security's encoder so the application layer depends only on the
 * port, leaving the algorithm swappable.
 */
@Component
class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    BCryptPasswordHasherAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String plaintext) {
        return passwordEncoder.encode(plaintext);
    }

    @Override
    public boolean matches(String plaintext, String hash) {
        return passwordEncoder.matches(plaintext, hash);
    }
}
