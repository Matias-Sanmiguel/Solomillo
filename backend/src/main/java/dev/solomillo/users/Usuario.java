package dev.solomillo.users;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String email;
    private String nombre;
    private String hashPassword;
    private String rol;

    public static final String[] ROLES = {
        "usuario_final", "analista_deportivo", "admin_sistema",
        "admin_modelos_ia", "cientifico_datos"
    };
}
