package pe.pucp.tel05.taxi.portal.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockMultipartFile;

import pe.pucp.tel05.taxi.portal.client.CloudinaryUploader.SubidaException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** La validacion corre antes de hablar con Cloudinary, asi que no necesita credenciales. */
class CloudinaryUploaderTest {

    private static MockMultipartFile archivo(String contentType, byte... bytes) {
        return new MockMultipartFile("foto", "foto.jpg", contentType, bytes);
    }

    private static byte[] conRelleno(int... inicio) {
        byte[] b = new byte[64];
        for (int i = 0; i < inicio.length; i++) {
            b[i] = (byte) inicio[i];
        }
        return b;
    }

    @Test
    @DisplayName("Acepta JPG, PNG y WebP segun sus primeros bytes")
    void aceptaImagenesReales() {
        assertDoesNotThrow(() -> CloudinaryUploader.validarImagen(
                archivo("image/jpeg", conRelleno(0xFF, 0xD8, 0xFF, 0xE0))));
        assertDoesNotThrow(() -> CloudinaryUploader.validarImagen(
                archivo("image/png", conRelleno(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))));
        assertDoesNotThrow(() -> CloudinaryUploader.validarImagen(
                archivo("image/webp", conRelleno('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))));
    }

    @Test
    @DisplayName("Rechaza HTML o texto aunque se llame .jpg y declare image/jpeg")
    void rechazaFalsificaciones() {
        assertThrows(SubidaException.class, () -> CloudinaryUploader.validarImagen(
                archivo("image/jpeg", "<html><script>alert(1)</script></html>".getBytes())));
        assertThrows(SubidaException.class, () -> CloudinaryUploader.validarImagen(
                archivo("image/png", "texto cualquiera".getBytes())));
    }

    @Test
    @DisplayName("Rechaza SVG (puede llevar scripts) y ejecutables")
    void rechazaSvgYEjecutables() {
        assertThrows(SubidaException.class, () -> CloudinaryUploader.validarImagen(
                archivo("image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes())));
        assertThrows(SubidaException.class, () -> CloudinaryUploader.validarImagen(
                archivo("application/octet-stream", conRelleno('M', 'Z'))));
    }

    @Test
    @DisplayName("Un archivo vacio o muy corto no pasa")
    void rechazaVacios() {
        assertThrows(SubidaException.class, () -> new CloudinaryUploader("").subir(archivo("image/png")));
        assertThrows(SubidaException.class, () -> CloudinaryUploader.validarImagen(
                archivo("image/jpeg", (byte) 0xFF, (byte) 0xD8)));
    }

    @Test
    @DisplayName("subir() valida el tipo antes de exigir Cloudinary")
    void subirValidaPrimero() {
        SubidaException e = assertThrows(SubidaException.class, () -> new CloudinaryUploader("")
                .subir(archivo("image/jpeg", "no soy imagen".getBytes())));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("JPG, PNG o WebP"));
    }
}
