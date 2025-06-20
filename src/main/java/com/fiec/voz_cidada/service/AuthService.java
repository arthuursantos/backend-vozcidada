package com.fiec.voz_cidada.service;

import com.fiec.voz_cidada.config.security.TokenService;
import com.fiec.voz_cidada.domain.auth_user.*;
import com.fiec.voz_cidada.domain.funcionario.Funcionario;
import com.fiec.voz_cidada.domain.usuario.Usuario;
import com.fiec.voz_cidada.exceptions.InvalidAuthenticationException;
import com.fiec.voz_cidada.exceptions.ResourceNotFoundException;
import com.fiec.voz_cidada.repository.AuthRepository;
import com.fiec.voz_cidada.repository.FuncionarioRepository;
import com.fiec.voz_cidada.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.View;

import java.util.List;

@Slf4j
@Service
public class AuthService implements UserDetailsService {

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    @Autowired TokenService tokenService;

    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;

    public ResponseEntity<?> login(AuthenticationDTO dto) {
        try {
            AuthenticationManager authManager = authenticationConfiguration.getAuthenticationManager();
            var credentials = new UsernamePasswordAuthenticationToken(dto.login(), dto.password());
            var auth = authManager.authenticate(credentials);
            return ResponseEntity.ok(tokenService.createAuthTokens((AuthUser) auth.getPrincipal()));
        } catch (Exception e) {
            throw new InvalidAuthenticationException("Seu login ou senha estão incorretos!");
        }
    }

    public AuthUser findById(Long id) {
        return authRepository.findById(id)
                .orElseThrow();
    }

    public ResponseEntity<?> loginWithGoogle(GoogleEmailDTO dto) {
        try {
            AuthUser user = (AuthUser) authRepository.findByLogin(dto.email());
            if (user == null) {
                user = new AuthUser(
                        dto.email(),
                        new BCryptPasswordEncoder().encode("google-oauth-" + dto.email()),
                        UserRole.USER,
                        AuthStatus.SIGNIN);

                AuthUser savedAuth = authRepository.save(user);
                StackTraceElement currentMethod = Thread.currentThread().getStackTrace()[1];
                String logMsg = "Usuário de autenticação criado com OAuth. ID " + savedAuth.getId();
                log.info("{} > {} > {}", currentMethod.getClassName(), currentMethod.getMethodName(), logMsg);

            }
            LoginResponseDTO tokens = tokenService.createAuthTokens(user);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            throw new InvalidAuthenticationException("Não foi possível se autenticar com a conta Google.");
        }
    }

    public ResponseEntity<?> createUser(RegisterDTO dto) {
        try {
            if (authRepository.findByLogin(dto.login()) != null) return ResponseEntity.badRequest().build();
            String encryptedPassword = new BCryptPasswordEncoder().encode(dto.password());
            AuthUser newUser = new AuthUser(dto.login(), encryptedPassword, UserRole.USER, AuthStatus.SIGNUP);

            authRepository.save(newUser);
            StackTraceElement currentMethod = Thread.currentThread().getStackTrace()[1];
            String logMsg = "Usuário de autenticação criado. ID " + newUser.getId();
            log.info("{} > {} > {}", currentMethod.getClassName(), currentMethod.getMethodName(), logMsg);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            throw new InvalidAuthenticationException("Não foi possível criar o usuário.");
        }
    }

    public ResponseEntity<?> createAdmin(RegisterDTO dto) {
        try {
            if (authRepository.findByLogin(dto.login()) != null) return ResponseEntity.badRequest().build();
            String encryptedPassword = new BCryptPasswordEncoder().encode(dto.password());
            AuthUser newUser = new AuthUser(dto.login(), encryptedPassword, UserRole.ADMIN, AuthStatus.SIGNUP);

            authRepository.save(newUser);
            StackTraceElement currentMethod = Thread.currentThread().getStackTrace()[1];
            String logMsg = "Usuário de autenticação com ROLE_ADMIN criado. ID " + newUser.getId();
            log.info("{} > {} > {}", currentMethod.getClassName(), currentMethod.getMethodName(), logMsg);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            throw new InvalidAuthenticationException("Não foi possível criar o administrador.");
        }
    }

    public ResponseEntity<?> changePassword(String token, ChangePasswordDTO dto) {
        try {
            String id = tokenService.validateAccessToken(token.replace("Bearer ", ""));
            if (id == null) {
                throw new InvalidAuthenticationException("Token inválido ou expirado.");
            }

            var user = authRepository.findById(Long.valueOf(id))
                    .orElseThrow(() -> new InvalidAuthenticationException("Usuário não encontrado."));

            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
                throw new InvalidAuthenticationException("Senha atual incorreta.");
            }

            String encryptedPassword = passwordEncoder.encode(dto.newPassword());
            user.changePassword(encryptedPassword);

            authRepository.save(user);
            StackTraceElement currentMethod = Thread.currentThread().getStackTrace()[1];
            String logMsg = "Senha alterada. AuthUser ID " + user.getId();
            log.info("{} > {} > {}", currentMethod.getClassName(), currentMethod.getMethodName(), logMsg);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            throw new InvalidAuthenticationException("Não foi possível alterar a senha.");
        }
    }

    public ResponseEntity<?> patchAuthStatus(String token) {
        try {

            String id = tokenService.validateAccessToken(token.replace("Bearer ", ""));
            if (id == null) {throw new InvalidAuthenticationException("Token inválido ou expirado.");}
            AuthUser user = authRepository.findById(Long.valueOf(id))
                    .orElseThrow(() -> new InvalidAuthenticationException("Usuário não autenticado."));

            if (user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                funcionarioRepository.findByAuthUser_Id(Long.valueOf(id))
                        .orElseThrow(() -> new ResourceNotFoundException("O usuário ainda não terminou seu cadastro."));

            } else {
                usuarioRepository.findByAuthUser_Id(Long.valueOf(id))
                        .orElseThrow(() -> new ResourceNotFoundException("O usuário ainda não terminou seu cadastro."));
            }
            user.updateAuthStatus("SIGNUP");
            authRepository.save(user);

            StackTraceElement currentMethod = Thread.currentThread().getStackTrace()[1];
            String logMsg = "Status de autenticação do usuário alterado. AuthUser ID: " + user.getId();
            log.info("{} > {} > {}", currentMethod.getClassName(), currentMethod.getMethodName(), logMsg);

            LoginResponseDTO tokens = tokenService.createAuthTokens(user);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new InvalidAuthenticationException("Não foi possível atualizar a autenticação do usuário.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return authRepository.findByLogin(username);
    }
}
