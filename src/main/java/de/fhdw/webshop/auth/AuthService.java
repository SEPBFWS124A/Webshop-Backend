package de.fhdw.webshop.auth;

import de.fhdw.webshop.auth.dto.AuthResponse;
import de.fhdw.webshop.auth.dto.LoginRequest;
import de.fhdw.webshop.auth.dto.RegisterRequest;
import de.fhdw.webshop.loyalty.LoyaltyService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklist tokenBlacklist;
    private final JdbcTemplate jdbcTemplate;
    private final LoyaltyService loyaltyService;

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password())
        );

        User authenticatedUser = (User) authentication.getPrincipal();
        if (authenticatedUser.hasRole(UserRole.CUSTOMER)) {
            loyaltyService.recordLogin(authenticatedUser);
        }
        String token = jwtTokenProvider.generateToken(authenticatedUser);
        return buildAuthResponse(token, authenticatedUser);
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsernameAndActiveTrue(registerRequest.username())) {
            throw new IllegalArgumentException("Username already taken: " + registerRequest.username());
        }
        if (userRepository.existsByEmailAndActiveTrue(registerRequest.email())) {
            throw new IllegalArgumentException("Email already registered: " + registerRequest.email());
        }

        User newUser = new User();
        newUser.setUsername(registerRequest.username());
        newUser.setEmail(registerRequest.email());
        newUser.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        newUser.getRoles().add(UserRole.CUSTOMER);
        newUser.setUserType(registerRequest.userType());

        // Generate a unique customer number from the DB sequence
        Long nextSequenceValue = jdbcTemplate.queryForObject(
                "SELECT nextval('customer_number_sequence')", Long.class);
        newUser.setCustomerNumber(String.valueOf(nextSequenceValue));

        User savedUser = userRepository.save(newUser);
        String token = jwtTokenProvider.generateToken(savedUser);
        return buildAuthResponse(token, savedUser);
    }

    public void logout(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring("Bearer ".length());
            tokenBlacklist.invalidate(token);
        }
    }

    private AuthResponse buildAuthResponse(String token, User user) {
        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles(),
                user.getUserType(),
                user.getCustomerNumber()
        );
    }
}
