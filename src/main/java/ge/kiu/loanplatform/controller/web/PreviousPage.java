package ge.kiu.loanplatform.controller.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

// Finds the /ui page the user came from, so the website can send them back there after an action
// (e.g. approve in the "Actions" menu of a list returns to that list).
// Only static methods: it keeps no data and needs no Spring beans, so it is not a bean itself.
public final class PreviousPage {

    private PreviousPage() {
        // not meant to be created: call PreviousPage.find(...) / PreviousPage.findOr(...)
    }

    // The page the user came from, or "fallback" when it is unknown
    public static String findOr(HttpServletRequest request, String fallback) {
        String previousPage = find(request);
        return previousPage != null ? previousPage : fallback;
    }

    // The browser sends the address of the page the user came from in the "Referer" header.
    // We use only its path, and only if it is one of our own /ui pages, so nobody can use
    // this to redirect a user to another website. null = unknown.
    public static String find(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            // going back to the page that just failed would fail again, so that does not count
            if (path == null || !path.startsWith("/ui") || path.equals(request.getRequestURI())) {
                return null;
            }
            return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
        } catch (IllegalArgumentException invalidAddress) {
            return null;
        }
    }
}
