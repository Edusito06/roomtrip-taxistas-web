package pe.pucp.tel05.taxi.portal.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import pe.pucp.tel05.taxi.portal.client.CloudinaryUploader;
import pe.pucp.tel05.taxi.portal.client.TaxiApiClient;

/**
 * Controlador del portal. Mantiene el token del Superadmin en la sesion HTTP
 * y delega todo lo demas en el gateway.
 *
 * <p>El token vive en la sesion del servidor y no en el navegador a proposito:
 * asi no queda expuesto a scripts en la pagina.
 */
@Controller
public class PortalController {

    private static final String SESION_TOKEN = "jwt";
    private static final String SESION_ROL = "rol";
    private static final String SESION_CORREO = "correo";
    private static final String SESION_ID = "id";

    private final TaxiApiClient api;
    private final CloudinaryUploader cloudinary;

    public PortalController(TaxiApiClient api, CloudinaryUploader cloudinary) {
        this.api = api;
        this.cloudinary = cloudinary;
    }

    @GetMapping("/")
    public String inicio(HttpSession sesion) {
        if (sesion.getAttribute(SESION_TOKEN) == null) {
            return "redirect:/login";
        }
        return "TAXISTA".equals(sesion.getAttribute(SESION_ROL))
                ? "redirect:/mi-cuenta" : "redirect:/admin/solicitudes";
    }

    // ------------------------------------------------------------------
    // Autenticacion (RF-WTX-009)
    // ------------------------------------------------------------------

    @GetMapping("/login")
    public String login(@RequestParam(value = "expirada", required = false) String expirada, Model modelo) {
        if (expirada != null) {
            modelo.addAttribute("aviso", "Tu sesión expiró por inactividad. Inicia sesión nuevamente.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String autenticar(@RequestParam String correo,
                             @RequestParam String password,
                             HttpSession sesion,
                             RedirectAttributes redirect) {
        try {
            Map<String, Object> respuesta = api.login(correo, password);
            String rol = String.valueOf(respuesta.get("rol"));
            sesion.setAttribute(SESION_TOKEN, respuesta.get("token"));
            sesion.setAttribute(SESION_ROL, rol);
            sesion.setAttribute(SESION_CORREO, correo);
            sesion.setAttribute(SESION_ID, respuesta.get("subject"));
            return "TAXISTA".equals(rol) ? "redirect:/mi-cuenta" : "redirect:/admin/solicitudes";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String salir(HttpSession sesion) {
        sesion.invalidate();
        return "redirect:/login";
    }

    // ------------------------------------------------------------------
    // Autoregistro del taxista (RF-WTX-001, RF-WTX-002)
    // ------------------------------------------------------------------

    @GetMapping("/registro")
    public String formularioRegistro() {
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@RequestParam Map<String, String> formulario,
                            @RequestParam("fotoTaxista") MultipartFile fotoTaxista,
                            @RequestParam("fotoVehiculo") MultipartFile fotoVehiculo,
                            RedirectAttributes redirect) {
        Map<String, Object> solicitud = new HashMap<>(formulario);
        try {
            solicitud.put("fotoUrl", cloudinary.subir(fotoTaxista));
            solicitud.put("fotoVehiculoUrl", cloudinary.subir(fotoVehiculo));
            api.registrarTaxista(solicitud);
            redirect.addFlashAttribute("exito",
                    "Tu solicitud fue enviada. Queda pendiente de aprobación por el administrador.");
            return "redirect:/registro";
        } catch (CloudinaryUploader.SubidaException | TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("datos", formulario);
            return "redirect:/registro";
        }
    }

    // ------------------------------------------------------------------
    // Panel del Superadmin (RF-WTX-004, RF-WTX-005)
    // ------------------------------------------------------------------

    @GetMapping("/admin/solicitudes")
    public String solicitudes(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        if (token == null) {
            return "redirect:/login";
        }
        try {
            List<Map<String, Object>> pendientes = api.listarPendientes(token);
            modelo.addAttribute("pendientes", pendientes);
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "admin/solicitudes";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/admin/solicitudes/{id}/decidir")
    public String decidir(@PathVariable String id,
                          @RequestParam boolean aprobado,
                          @RequestParam(required = false) String motivo,
                          HttpSession sesion,
                          RedirectAttributes redirect) {
        String token = token(sesion);
        if (token == null) {
            return "redirect:/login";
        }
        try {
            api.decidirSolicitud(token, id, aprobado, motivo);
            redirect.addFlashAttribute("exito",
                    aprobado ? "Taxista aprobado y habilitado." : "Solicitud rechazada.");
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/solicitudes";
    }

    @GetMapping("/admin/taxistas")
    public String taxistas(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        if (token == null) {
            return "redirect:/login";
        }
        try {
            List<Map<String, Object>> taxistas = api.listarTodos(token);
            modelo.addAttribute("taxistas", taxistas);
            modelo.addAttribute("total", taxistas.size());
            modelo.addAttribute("aprobados", contar(taxistas, "estadoHabilitacion", "APROBADO"));
            modelo.addAttribute("pendientesCount", contar(taxistas, "estadoHabilitacion", "PENDIENTE"));
            modelo.addAttribute("disponibles", contar(taxistas, "estadoDisponibilidad", "DISPONIBLE"));
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "admin/taxistas";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    /** Servicios de taxi con el taxista asignado y, si esta en curso, su ultima ubicacion. */
    @GetMapping("/admin/servicios")
    public String servicios(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        if (token == null) {
            return "redirect:/login";
        }
        try {
            Map<String, String> nombres = new HashMap<>();
            for (Map<String, Object> t : api.listarTodos(token)) {
                nombres.put(String.valueOf(t.get("id")), t.get("nombres") + " " + t.get("apellidos"));
            }
            List<Map<String, Object>> servicios = api.listarServicios(token);
            for (Map<String, Object> s : servicios) {
                Object taxistaId = s.get("taxistaId");
                if (taxistaId == null) {
                    continue;
                }
                s.put("taxistaNombre", nombres.getOrDefault(String.valueOf(taxistaId), "—"));
                Object estado = s.get("estado");
                if ("ASIGNADO".equals(estado) || "EN_CAMINO".equals(estado) || "EN_TRASLADO".equals(estado)) {
                    try {
                        Map<String, Object> u = api.ubicacionTaxista(token, String.valueOf(taxistaId));
                        if (u.get("latitud") != null && u.get("longitud") != null) {
                            s.put("latitud", u.get("latitud"));
                            s.put("longitud", u.get("longitud"));
                            s.put("ubicacionEn", u.get("actualizadaEn"));
                        }
                    } catch (TaxiApiClient.ApiException ignorada) {
                        // Sin ubicacion todavia: la fila se muestra igual, sin boton de mapa.
                    }
                }
            }
            modelo.addAttribute("servicios", servicios);
            modelo.addAttribute("enCurso", servicios.stream()
                    .filter(s -> !"FINALIZADO".equals(s.get("estado"))).count());
            modelo.addAttribute("finalizados", contar(servicios, "estado", "FINALIZADO"));
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "admin/servicios";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @GetMapping("/admin/eventos")
    public String eventos(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        if (token == null) {
            return "redirect:/login";
        }
        try {
            modelo.addAttribute("eventos", api.listarEventos(token));
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "admin/eventos";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    private long contar(List<Map<String, Object>> taxistas, String campo, String valor) {
        return taxistas.stream().filter(t -> valor.equals(t.get(campo))).count();
    }

    // ------------------------------------------------------------------
    // Pantalla del taxista (RF-WTX-005, RF-WTX-007)
    // ------------------------------------------------------------------

    @GetMapping("/mi-cuenta")
    public String miCuenta(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        String id = id(sesion);
        if (token == null || id == null) {
            return "redirect:/login";
        }
        try {
            modelo.addAttribute("taxista", api.obtenerTaxista(token, id));
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "mi-cuenta";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/mi-cuenta/disponibilidad")
    public String cambiarDisponibilidad(@RequestParam String estado,
                                        HttpSession sesion,
                                        RedirectAttributes redirect) {
        String token = token(sesion);
        String id = id(sesion);
        if (token == null || id == null) {
            return "redirect:/login";
        }
        try {
            api.cambiarDisponibilidad(token, id, estado);
            redirect.addFlashAttribute("exito", "Tu disponibilidad ahora es " + estado + ".");
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mi-cuenta";
    }

    @GetMapping("/mi-cuenta/editar")
    public String formularioEditarPerfil(HttpSession sesion, Model modelo, RedirectAttributes redirect) {
        String token = token(sesion);
        String id = id(sesion);
        if (token == null || id == null) {
            return "redirect:/login";
        }
        try {
            modelo.addAttribute("taxista", api.obtenerTaxista(token, id));
            modelo.addAttribute("correo", sesion.getAttribute(SESION_CORREO));
            return "mi-cuenta-editar";
        } catch (TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-cuenta";
        }
    }

    /**
     * RF-WTX-005. Las fotos son opcionales aqui: si el taxista no elige un
     * archivo nuevo, el formulario ya trae la URL actual en un campo oculto
     * (mismo nombre que espera la API) y esa es la que se reenvia tal cual.
     */
    @PostMapping("/mi-cuenta/editar")
    public String editarPerfil(@RequestParam Map<String, String> formulario,
                               @RequestParam(value = "fotoTaxista", required = false) MultipartFile fotoTaxista,
                               @RequestParam(value = "fotoVehiculo", required = false) MultipartFile fotoVehiculo,
                               HttpSession sesion,
                               RedirectAttributes redirect) {
        String token = token(sesion);
        String id = id(sesion);
        if (token == null || id == null) {
            return "redirect:/login";
        }
        Map<String, Object> datos = new HashMap<>(formulario);
        try {
            if (fotoTaxista != null && !fotoTaxista.isEmpty()) {
                datos.put("fotoUrl", cloudinary.subir(fotoTaxista));
            }
            if (fotoVehiculo != null && !fotoVehiculo.isEmpty()) {
                datos.put("fotoVehiculoUrl", cloudinary.subir(fotoVehiculo));
            }
            api.actualizarPerfil(token, id, datos);
            if (formulario.get("correo") != null) {
                sesion.setAttribute(SESION_CORREO, formulario.get("correo"));
            }
            redirect.addFlashAttribute("exito", "Tu perfil se actualizo correctamente.");
            return "redirect:/mi-cuenta";
        } catch (CloudinaryUploader.SubidaException | TaxiApiClient.ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/mi-cuenta/editar";
        }
    }

    private String token(HttpSession sesion) {
        Object token = sesion.getAttribute(SESION_TOKEN);
        return token == null ? null : token.toString();
    }

    private String id(HttpSession sesion) {
        Object id = sesion.getAttribute(SESION_ID);
        return id == null ? null : id.toString();
    }
}
