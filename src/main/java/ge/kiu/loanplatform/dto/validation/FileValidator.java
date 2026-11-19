package ge.kiu.loanplatform.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

// Checks a file annotated with @ValidFile (presence, size, declared type, and for images the real
// format and the number of pixels). Bean Validation creates this class itself
// (it is not a Spring bean, so it needs no stereotype annotation).
// The rules are in the static method findProblem, so FileStorageService can run exactly the same
// check again (the website upload does not go through this annotation).
public class FileValidator implements ConstraintValidator<ValidFile, MultipartFile> {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB in bytes

    // Pixel bombs: a small file (a few KB) can say it is 100 000 x 100 000 pixels. Whoever decodes it
    // (a browser, an image viewer, a future thumbnail feature) needs width x height x 4 bytes of memory,
    // here 40 GB. So an image may have at most 25 million pixels (about 100 MB when decoded);
    // that is more than any phone photo or scan of an ID card needs (a 12 MP photo has 12 million).
    public static final long MAX_IMAGE_PIXELS = 25_000_000;

    // allowed file extensions, each with the Content-Type it must be declared with
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        String problem = findProblem(file);
        if (problem == null) {
            return true;
        }
        // Replace the default message ("Invalid file") with the specific one
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
        return false;
    }

    // Returns what is wrong with the file, or null when it is fine
    public static String findProblem(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "File is required and must not be empty";
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return "File is too large. Maximum size is 5MB";
        }
        // the declared type: the extension of the name and the Content-Type sent with the file must match
        // ("photo.png" sent as image/png is fine; "notes.pdf" sent as text/plain is not)
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extensionOf(file.getOriginalFilename()));
        if (expectedContentType == null || !expectedContentType.equalsIgnoreCase(file.getContentType())) {
            return "Only PDF, JPG and PNG files are allowed";
        }
        if (expectedContentType.startsWith("image/")) {
            return findImageProblem(file, expectedContentType);
        }
        return null;
    }

    // Reads ONLY the header of the image (the first bytes, where width and height are written).
    // The pixels are never decoded, so even a pixel bomb costs no memory here.
    // It also proves the bytes really are a PNG or JPEG, not something renamed to "photo.png".
    private static String findImageProblem(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {

            // ImageIO looks at the first bytes and returns the readers that understand them
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return "The file is not a valid JPG or PNG image";
            }
            ImageReader reader = readers.next();
            try {
                // the real format must be the declared one ("png" for image/png, "jpeg" for image/jpeg)
                String format = reader.getFormatName().toLowerCase();
                if (!contentType.endsWith(format)) {
                    return "The file content does not match its type " + contentType;
                }
                reader.setInput(imageInput, true, true); // true, true = read forward only, skip metadata
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0); // header only, no decoding
                if (pixels > MAX_IMAGE_PIXELS) {
                    return "The image is too large. Maximum is 25 million pixels";
                }
            } finally {
                reader.dispose(); // frees what the reader holds
            }
        } catch (IOException e) {
            // a broken header (e.g. a truncated file) cannot be read
            return "The file is not a valid JPG or PNG image";
        }
        return null;
    }

    // "Salary.Certificate.PDF" -> "pdf", no dot -> ""
    private static String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
