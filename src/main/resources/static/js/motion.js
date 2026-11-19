/*
  Two small animations that CSS cannot do alone. Plain JavaScript, no library.

  1. Count up: an element with the attribute data-count-up (e.g. <span data-count-up>12</span>)
     counts from 0 to its number.
  2. Reveal: an element with the class "reveal" fades in when it is scrolled into view
     (the look is in style.css, section 12). Numbers inside it count up at that moment,
     not before, so the animation is actually seen.

  People who asked their system for less motion get neither: the numbers and the parts are just shown.
*/

// true when the system setting "reduce motion" is on
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

// The class "js" on <html> tells style.css that this script runs, so it may hide the .reveal parts
// until they are scrolled into view. Without JavaScript they are never hidden.
if (!reduceMotion) {
    document.documentElement.classList.add("js");
}

// the code below needs the page, so it waits until the HTML is read
document.addEventListener("DOMContentLoaded", () => {
    if (reduceMotion) {
        return;
    }
    // numbers that are not inside a .reveal part (e.g. the one in the banner) count up right away
    document.querySelectorAll("[data-count-up]").forEach((element) => {
        if (!element.closest(".reveal")) {
            countUp(element);
        }
    });
    revealOnScroll();
});

// counts the number in "element" up from 0 to its value
function countUp(element) {
    const duration = 900; // milliseconds
    const target = Number(element.textContent);
    // only whole numbers (like 12) count up; anything else stays as it is
    if (!Number.isInteger(target) || target <= 0) {
        return;
    }

    const start = performance.now();

    // called by the browser before every new frame (about 60 times per second)
    function step(now) {
        const progress = Math.min((now - start) / duration, 1); // 0 at the start, 1 at the end
        const eased = 1 - Math.pow(1 - progress, 3);              // fast at first, slow at the end
        element.textContent = Math.round(target * eased);
        if (progress < 1) {
            requestAnimationFrame(step);
        }
    }

    element.textContent = "0";
    requestAnimationFrame(step);
}

function revealOnScroll() {
    // IntersectionObserver tells us when an element enters the visible part of the page
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                entry.target.classList.add("is-visible"); // style.css fades it in
                entry.target.querySelectorAll("[data-count-up]").forEach(countUp);
                observer.unobserve(entry.target);          // once is enough
            }
        });
    }, { threshold: 0.15 }); // when 15% of the element is visible

    document.querySelectorAll(".reveal").forEach((element) => observer.observe(element));
}
