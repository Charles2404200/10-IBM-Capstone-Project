package com.ibm.consulting.sim.identity.application;

import com.ibm.consulting.sim.identity.domain.*;
import com.ibm.consulting.sim.identity.infrastructure.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public TokenResponse execute(String email, String password, String displayName) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }
        User user = User.create(email, passwordEncoder.encode(password), displayName, UserRole.LEARNER);
        userRepository.save(user);
        return new TokenResponse(jwtTokenProvider.generateToken(user), user.getId().toString(),
                user.getDisplayName(), user.getRole().name());
    }
}
