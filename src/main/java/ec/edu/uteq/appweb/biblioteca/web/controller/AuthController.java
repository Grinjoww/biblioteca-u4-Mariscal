package ec.edu.uteq.appweb.biblioteca.web.controller;

import ec.edu.uteq.appweb.biblioteca.domain.Usuario;
import ec.edu.uteq.appweb.biblioteca.repository.UsuarioRepository;
import ec.edu.uteq.appweb.biblioteca.security.JwtService;
import ec.edu.uteq.appweb.biblioteca.web.dto.ApiResponse;
import ec.edu.uteq.appweb.biblioteca.web.dto.LoginRequest;
import ec.edu.uteq.appweb.biblioteca.web.dto.LoginResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest solicitud) {
        Optional<Usuario> encontrado = usuarios.findByUsernameAndActivoTrue(solicitud.username());
        if (encontrado.isEmpty()
                || !encoder.matches(solicitud.password(), encontrado.get().getPasswordHash())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(credencialesInvalidas());
        }
        Usuario usuario = encontrado.get();
        String token = jwtService.generar(usuario);
        long expiraEn = jwtService.expiracionEnSegundos();
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(false) 
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(expiraEn))
                .build();
        LoginResponse cuerpo = new LoginResponse(
                usuario.getUsername(), usuario.getRol().name(), "Bearer", expiraEn);

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(cuerpo, "Autenticacion correcta"));
    }
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout() {
        ResponseCookie borrar = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, borrar.toString())
                .body(ApiResponse.<String>ok(null, "Sesion cerrada"));
    }
    private ProblemDetail credencialesInvalidas() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Usuario o contrasena incorrectos");
        problema.setTitle("Credenciales invalidas");
        problema.setType(URI.create("https://uteq.edu.ec/errores/credenciales-invalidas"));
        problema.setProperty("timestamp", OffsetDateTime.now().toString());
        return problema;
    }
}