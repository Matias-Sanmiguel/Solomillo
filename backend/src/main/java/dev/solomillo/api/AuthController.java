package dev.solomillo.api;

import dev.solomillo.core.JwtUtil;
import dev.solomillo.repository.UsuarioRepository;
import dev.solomillo.users.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthController(UsuarioRepository u, PasswordEncoder e, JwtUtil j) {
        this.usuarioRepo = u; this.encoder = e; this.jwtUtil = j;
    }

    record RegisterIn(@Email @NotBlank String email, @NotBlank String nombre,
                      @NotBlank String password, String rol) {}
    record LoginIn(@Email @NotBlank String email, @NotBlank String password) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterIn body) {
        String rol = body.rol() != null ? body.rol() : "usuario_final";
        if (Arrays.stream(Usuario.ROLES).noneMatch(r -> r.equals(rol)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido");
        if (usuarioRepo.existsByEmail(body.email()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya registrado");

        var u = new Usuario();
        u.setEmail(body.email()); u.setNombre(body.nombre());
        u.setHashPassword(encoder.encode(body.password())); u.setRol(rol);
        usuarioRepo.save(u);
        return Map.of("access_token", jwtUtil.createToken(u.getEmail(), u.getRol()),
                "token_type", "bearer", "rol", u.getRol());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginIn body) {
        var user = usuarioRepo.findByEmail(body.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        if (!encoder.matches(body.password(), user.getHashPassword()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        return Map.of("access_token", jwtUtil.createToken(user.getEmail(), user.getRol()),
                "token_type", "bearer", "rol", user.getRol());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal String email) {
        var user = usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("email", user.getEmail(), "nombre", user.getNombre(), "rol", user.getRol());
    }
}
