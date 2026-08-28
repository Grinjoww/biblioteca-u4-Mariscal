package ec.edu.uteq.appweb.biblioteca.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.uteq.appweb.biblioteca.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String BASE_TIPO = "https://uteq.edu.ec/errores/";
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFiltro,
                                           ObjectMapper json) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/api/docs/**", "/api/docs").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint(json))
                        .accessDeniedHandler(accessDeniedHandler(json)))
                .addFilterBefore(jwtFiltro, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    private AuthenticationEntryPoint entryPoint(ObjectMapper json) {
        return (peticion, respuesta, excepcion) -> escribir(respuesta, json,
                HttpStatus.UNAUTHORIZED, "No autenticado",
                "Debe presentar un token valido en la cabecera Authorization", "no-autenticado");
    }
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper json) {
        return (peticion, respuesta, excepcion) -> escribir(respuesta, json,
                HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tiene permisos suficientes para ejecutar esta operacion", "acceso-denegado");
    }
    private static void escribir(HttpServletResponse respuesta, ObjectMapper json,
                                 HttpStatus estado, String titulo, String detalle, String tipo)
            throws java.io.IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setType(URI.create(BASE_TIPO + tipo));
        problema.setProperty("timestamp", OffsetDateTime.now().toString());

        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}