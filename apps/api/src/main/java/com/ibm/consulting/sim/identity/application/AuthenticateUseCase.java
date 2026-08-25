package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.*;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthenticateUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticateUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional(readOnly = true)
    public TokenResponse execute(String email, String password) {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailVerificationRequiredException();
        }
        return new TokenResponse(jwtTokenProvider.generateToken(user), user.getId().toString(),
                user.getDisplayName(), user.getRole().name());
    }
}
