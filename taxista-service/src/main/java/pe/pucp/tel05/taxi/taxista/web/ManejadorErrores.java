package pe.pucp.tel05.taxi.taxista.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.pucp.tel05.taxi.taxista.service.AuthService;
import pe.pucp.tel05.taxi.taxista.service.TaxistaService;

/**
 * Traduce excepciones de dominio a codigos HTTP con un cuerpo uniforme.
 * La app Android depende de este contrato para mostrar mensajes claros
 * en lugar de errores crudos.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> cuerpo = base(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS",
                "Faltan datos obligatorios o tienen formato invalido");
        cuerpo.put("campos", campos);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(TaxistaService.ReglaNegocioException.class)
    public ResponseEntity<Map<String, Object>> reglaNegocio(TaxistaService.ReglaNegocioException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(base(HttpStatus.CONFLICT, "REGLA_NEGOCIO", ex.getMessage()));
    }

    @ExceptionHandler(TaxistaService.RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(TaxistaService.RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(base(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(AuthService.DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, Object>> demasiadosIntentos(AuthService.DemasiadosIntentosException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getSegundos()))
                .body(base(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", ex.getMessage()));
    }

    @ExceptionHandler(AuthService.CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> credenciales(AuthService.CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(base(HttpStatus.UNAUTHORIZED, "NO_AUTORIZADO", ex.getMessage()));
    }

    private Map<String, Object> base(HttpStatus status, String codigo, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", codigo);
        cuerpo.put("mensaje", mensaje);
        return cuerpo;
    }
}
