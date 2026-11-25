/**
 * The small test website (Thymeleaf pages) under /ui: one controller per area, like controller.api.
 * Difference from the REST controllers:
 * - @Controller (not @RestController): a method returns the NAME of an HTML template
 *   from src/main/resources/templates, not JSON.
 * - The controllers call the same services as the REST API, so all business rules and
 *   validation annotations are exactly the same.
 * - HTML forms can only send GET and POST, so update/approve/reject/delete are POST here.
 *
 * How a form works here:
 * - valid   -> call the service, then redirect with a green "flash" message.
 * - invalid -> show the SAME page again with what the user typed ("values")
 *              and one message per field ("fieldErrors"), so nothing has to be retyped (WebForms).
 *
 * Shared by every controller in this package (WebControllerAdvice): empty fields become null, and a refused
 * action sends the user back to the page they came from with the reason (PreviousPage).
 */
package ge.kiu.loanplatform.controller.web;
