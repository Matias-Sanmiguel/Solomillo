package dev.solomillo.users;

import dev.solomillo.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void registrar(String usuario, String accion, String resultado, String detalle) {
        var log = new AuditLog();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setResultado(resultado);
        log.setDetalle(detalle);
        repo.save(log);
    }
}
