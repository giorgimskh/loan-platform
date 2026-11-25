package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.exception.custom.BusinessRuleException;
import ge.kiu.loanplatform.exception.custom.ConflictException;
import ge.kiu.loanplatform.exception.custom.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * What every website controller shares: how form fields are read and how a refused action is shown.
 * - @ControllerAdvice: its @InitBinder and @ExceptionHandler methods apply to many controllers at once.
 * - basePackageClasses: only to the controllers in this package (controller.web), so the REST API
 *   keeps getting JSON errors from GlobalExceptionHandler.
 * - @Order(HIGHEST_PRECEDENCE): GlobalExceptionHandler handles the same exceptions for every controller;
 *   Spring asks the advice classes in order, so this one must come first to win on the website.
 */
@ControllerAdvice(basePackageClasses = WebControllerAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebControllerAdvice {

    // An empty form field arrives as "" (empty text). This turns "" into null,
    // so an empty optional field (phone) is treated as "not sent", like in JSON.
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // When a service throws one of our custom exceptions on a /ui page (e.g. rule 4: cancelling an
    // APPROVED application, or approving it a second time), send the user back to the page they came from
    // and show the reason there.
    @ExceptionHandler({ResourceNotFoundException.class, BusinessRuleException.class, ConflictException.class})
    public String handleServiceError(RuntimeException ex, HttpServletRequest request,
                                     Model model, RedirectAttributes redirect) {
        String previousPage = PreviousPage.find(request);
        if (previousPage != null) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + previousPage;
        }
        // no page to go back to (e.g. the address was typed by hand): show a separate error page
        model.addAttribute("errorType", ex.getClass().getSimpleName());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error-message";
    }
}
