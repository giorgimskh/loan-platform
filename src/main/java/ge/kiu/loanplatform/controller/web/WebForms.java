package ge.kiu.loanplatform.controller.web;

import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.LinkedHashMap;
import java.util.Map;

// How every website form is shown again after a problem, so nothing has to be retyped:
// "values" = what the user typed, "fieldErrors" = one message per field, "formError" = one message above the form.
// Only static methods: it keeps no data and needs no Spring beans, so it is not a bean itself.
public final class WebForms {

    private WebForms() {
        // not meant to be created: call WebForms.showFieldErrors(...) / WebForms.showFormError(...)
    }

    // Validation failed: give the template the typed values and one message per field
    public static void showFieldErrors(Model model, Map<String, String> params, BindingResult result) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : result.getFieldErrors()) {
            // an invalid element of a Set is reported as "requiredDocumentTypeIds[]"
            String field = error.getField().replace("[]", "");
            // isBindingFailure = the text could not be converted at all, e.g. "abc" for a number
            String message = error.isBindingFailure() ? "Enter a valid value" : error.getDefaultMessage();
            fieldErrors.putIfAbsent(field, message);
        }
        model.addAttribute("values", params);
        model.addAttribute("fieldErrors", fieldErrors);
    }

    // The service refused the request (business rule or conflict): keep the form, show the reason above it
    public static void showFormError(Model model, Map<String, String> params, String message) {
        model.addAttribute("values", params);
        model.addAttribute("formError", message);
    }
}
