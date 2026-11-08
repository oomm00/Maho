/* Maho landing page — tiny, dependency-free enhancements. */

(function () {
  "use strict";

  /* ---------------------------------------------------------
     Year
     --------------------------------------------------------- */
  var year = document.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());

  /* ---------------------------------------------------------
     Theme toggle
     --------------------------------------------------------- */
  var root = document.documentElement;
  var toggle = document.querySelector(".theme-toggle");
  var themeMeta = document.querySelector('meta[name="theme-color"]');

  function syncTheme() {
    var dark = root.dataset.theme === "dark";
    if (toggle) {
      toggle.setAttribute("aria-label", dark ? "Switch to light theme" : "Switch to dark theme");
      toggle.setAttribute("title", dark ? "Switch to light theme" : "Switch to dark theme");
    }
    if (themeMeta) themeMeta.setAttribute("content", dark ? "#0E0E0E" : "#F6F3EC");
  }

  if (toggle) {
    syncTheme();
    toggle.addEventListener("click", function () {
      var next = root.dataset.theme === "dark" ? "light" : "dark";
      root.dataset.theme = next;
      try {
        localStorage.setItem("maho-theme", next);
      } catch (e) {}
      syncTheme();
    });
  }

  /* ---------------------------------------------------------
     Reveal on scroll
     --------------------------------------------------------- */
  var revealItems = Array.prototype.slice.call(document.querySelectorAll(".reveal"));
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (reduceMotion || !("IntersectionObserver" in window)) {
    revealItems.forEach(function (el) {
      el.classList.add("is-visible");
    });
  } else {
    document.querySelectorAll(".feature-grid .reveal").forEach(function (el, i) {
      el.style.transitionDelay = (i % 3) * 70 + "ms";
    });
    document.querySelectorAll(".steps .reveal").forEach(function (el, i) {
      el.style.transitionDelay = i * 90 + "ms";
    });

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
    );

    revealItems.forEach(function (el) {
      observer.observe(el);
    });
  }

  /* ---------------------------------------------------------
     Active nav link
     --------------------------------------------------------- */
  var sections = Array.prototype.slice.call(
    document.querySelectorAll("#features, #how, #download, #faq")
  );
  var navLinks = Array.prototype.slice.call(document.querySelectorAll(".nav__links a"));

  function setActive(id) {
    navLinks.forEach(function (link) {
      link.classList.toggle("is-active", link.getAttribute("href") === "#" + id);
    });
  }

  if ("IntersectionObserver" in window && sections.length) {
    var navObserver = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) setActive(entry.target.id);
        });
      },
      { rootMargin: "-35% 0px -55% 0px", threshold: 0 }
    );
    sections.forEach(function (section) {
      navObserver.observe(section);
    });
  }

  /* ---------------------------------------------------------
     Copy SHA-256
     --------------------------------------------------------- */
  var copyBtn = document.querySelector("[data-copy]");
  if (copyBtn) {
    var label = copyBtn.querySelector("span");
    var resetTimer;

    copyBtn.addEventListener("click", function () {
      var value = copyBtn.getAttribute("data-copy") || "";
      var done = function () {
        if (!label) return;
        label.textContent = "Copied";
        clearTimeout(resetTimer);
        resetTimer = setTimeout(function () {
          label.textContent = "Copy";
        }, 1600);
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(value).then(done, fallback);
      } else {
        fallback();
      }

      function fallback() {
        var textarea = document.createElement("textarea");
        textarea.value = value;
        textarea.setAttribute("readonly", "");
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.select();
        try {
          document.execCommand("copy");
        } catch (e) {}
        document.body.removeChild(textarea);
        done();
      }
    });
  }
})();
