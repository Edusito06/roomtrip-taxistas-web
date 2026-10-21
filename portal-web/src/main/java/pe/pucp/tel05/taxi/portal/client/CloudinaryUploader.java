package pe.pucp.tel05.taxi.portal.client;

import java.io.IOException;
import java.util.Map;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Sube las fotos del formulario de registro directo a Cloudinary (RF-WTX-002).
 *
 * <p>Antes el formulario pedia la URL ya subida a mano; ahora el portal recibe
 * el archivo y hace el upload el mismo, para que el taxista no necesite saber
 * que existe Cloudinary.
 */
@Component
public class CloudinaryUploader {

    private final Cloudinary cloudinary;

    public CloudinaryUploader(@Value("${app.cloudinary-url:}") String cloudinaryUrl) {
        this.cloudinary = (cloudinaryUrl == null || cloudinaryUrl.isBlank())
                ? null
                : new Cloudinary(cloudinaryUrl);
    }

    public String subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SubidaException("Debes adjuntar una imagen");
        }
        validarImagen(archivo);
        if (cloudinary == null) {
            throw new SubidaException(
                    "El portal no tiene configurado Cloudinary (variable CLOUDINARY_URL). "
                    + "Pide al administrador que la configure en el .env.");
        }
        try {
            Map<?, ?> resultado = cloudinary.uploader().upload(archivo.getBytes(), ObjectUtils.emptyMap());
            Object url = resultado.get("secure_url");
            if (url == null) {
                throw new SubidaException("Cloudinary no devolvio la URL de la imagen subida.");
            }
            return url.toString();
        } catch (IOException e) {
            throw new SubidaException("No se pudo leer el archivo para subirlo a Cloudinary.");
        } catch (SubidaException e) {
            throw e;
        } catch (RuntimeException e) {
            // El SDK de Cloudinary lanza distintos tipos de RuntimeException segun la
            // falla (credenciales invalidas, cuenta mal configurada, error de la API):
            // todas se traducen aqui para no dejar pasar un 500 crudo hasta el formulario.
            throw new SubidaException("No se pudo subir la imagen a Cloudinary: " + e.getMessage());
        }
    }

    /**
     * Solo JPG, PNG o WebP, comprobado por los primeros bytes del archivo y no por el
     * Content-Type ni la extension, que el navegador (o un atacante) puede poner a gusto.
     * SVG y HTML quedan fuera a proposito: pueden llevar scripts.
     */
    static void validarImagen(MultipartFile archivo) {
        byte[] cabecera = new byte[12];
        int leidos;
        try (java.io.InputStream in = archivo.getInputStream()) {
            leidos = in.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            throw new SubidaException("No se pudo leer el archivo para validarlo.");
        }
        boolean jpeg = leidos >= 3 && (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8
                && (cabecera[2] & 0xFF) == 0xFF;
        boolean png = leidos >= 8 && (cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 'P'
                && cabecera[2] == 'N' && cabecera[3] == 'G' && cabecera[4] == 0x0D
                && cabecera[5] == 0x0A && cabecera[6] == 0x1A && cabecera[7] == 0x0A;
        boolean webp = leidos >= 12 && cabecera[0] == 'R' && cabecera[1] == 'I' && cabecera[2] == 'F'
                && cabecera[3] == 'F' && cabecera[8] == 'W' && cabecera[9] == 'E' && cabecera[10] == 'B'
                && cabecera[11] == 'P';
        if (!(jpeg || png || webp)) {
            throw new SubidaException("La foto debe ser una imagen JPG, PNG o WebP.");
        }
    }

    /** Se traduce en un mensaje legible en el formulario, nunca un stacktrace. */
    public static class SubidaException extends RuntimeException {
        public SubidaException(String mensaje) {
            super(mensaje);
        }
    }
}
