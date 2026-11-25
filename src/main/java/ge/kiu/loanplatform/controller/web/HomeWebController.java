package ge.kiu.loanplatform.controller.web;

import ge.kiu.loanplatform.entity.application.ApplicationStatus;
import ge.kiu.loanplatform.service.application.LoanApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// The home page: the applications waiting for a decision, the key figures and the charts.
// Shared by every website controller: WebControllerAdvice, WebForms, PreviousPage.
@Controller
@RequestMapping("/ui")
@RequiredArgsConstructor
public class HomeWebController {

    private final LoanApplicationService loanApplicationService;

    // both /ui and /ui/ open the home page
    @GetMapping({"", "/"})
    public String home(Model model) {
        // Model = the data the template can read, e.g. ${totals}
        // the 5 newest applications that still need a decision
        Pageable newestFive = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("waiting", loanApplicationService.getAll(ApplicationStatus.SUBMITTED, newestFive));
        model.addAttribute("totals", loanApplicationService.getApprovedTotalsPerProduct());
        model.addAttribute("stats", loanApplicationService.getStatistics()); // the numbers and charts at the top
        return "home"; // templates/home.html
    }
}
