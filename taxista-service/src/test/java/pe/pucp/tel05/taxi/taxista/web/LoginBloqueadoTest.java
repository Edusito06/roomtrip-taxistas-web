package pe.pucp.tel05.taxi.taxista.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.pucp.tel05.taxi.taxista.security.SecurityConfig;
import pe.pucp.tel05.taxi.taxista.service.AuthService;
import pe.pucp.tel05.taxi.taxista.service.EventoService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del bloqueo: 429, cabecera Retry-After y un solo evento por bloqueo. */
@WebMvcTest(controllers = AuthController.class,
        properties = "app.jwt.secret=prueba-secreto-de-al-menos-32-caracteres-1234")
@Import({SecurityConfig.class, ManejadorErrores.class})
class LoginBloqueadoTest {

    private static final String CUERPO = "{\"correo\":\"a@x.pe\",\"password\":\"mala\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;
    @MockitoBean
    private EventoService eventos;

    @Test
    @DisplayName("Un bloqueo responde 429 con Retry-After y un mensaje legible")
    void responde429() throws Exception {
        when(auth.login(any(), any())).thenThrow(new AuthService.DemasiadosIntentosException(600, false));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.error").value("DEMASIADOS_INTENTOS"))
                .andExpect(jsonPath("$.mensaje").value("Demasiados intentos fallidos. Intenta de nuevo en 10 minuto(s)."));
    }

    @Test
    @DisplayName("Solo el intento que dispara el bloqueo queda en el log de eventos")
    void registraElBloqueoUnaSolaVez() throws Exception {
        when(auth.login(any(), any()))
                .thenThrow(new AuthService.DemasiadosIntentosException(900, true))
                .thenThrow(new AuthService.DemasiadosIntentosException(800, false))
                .thenThrow(new AuthService.DemasiadosIntentosException(700, false));

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isTooManyRequests());
        }

        verify(eventos, times(1)).registrar(eq("LOGIN_BLOQUEADO"), any(), any(), any());
        verify(eventos, never()).registrar(eq("LOGIN_FALLIDO"), any(), any(), any());
    }
}
