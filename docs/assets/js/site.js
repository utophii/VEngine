(function () {
  var content = document.querySelector(".content");

  // tables scroll inside their own frame instead of widening the page
  content.querySelectorAll("table").forEach(function (table) {
    var wrap = document.createElement("div");
    wrap.className = "tablewrap";
    table.parentNode.insertBefore(wrap, table);
    wrap.appendChild(table);
  });

  var headings = content.querySelectorAll("h2[id], h3[id]");
  headings.forEach(function (h) {
    var a = document.createElement("a");
    a.className = "anchor";
    a.href = "#" + h.id;
    a.setAttribute("aria-label", "Link to this section");
    a.textContent = "#";
    h.prepend(a);
  });

  // "On this page" under the current sidebar item, with the visible section highlighted
  var toc = document.querySelector(".sitenav ol.toc");
  var sections = content.querySelectorAll("h2[id]");
  if (toc && sections.length > 1) {
    var links = {};
    sections.forEach(function (h) {
      var li = document.createElement("li");
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.textContent = h.textContent.replace(/^#/, "").replace(/^\d+\.\s*/, "");
      li.appendChild(a);
      toc.appendChild(li);
      links[h.id] = a;
    });
    var active = null;
    var setActive = function (id) {
      if (active) active.classList.remove("active");
      active = links[id] || null;
      if (active) active.classList.add("active");
    };
    var onScroll = function () {
      var id = null;
      sections.forEach(function (h) {
        if (h.getBoundingClientRect().top < 120) id = h.id;
      });
      setActive(id);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }

  content.querySelectorAll("div.highlighter-rouge").forEach(function (block) {
    var code = block.querySelector("pre code");
    if (!code || !navigator.clipboard) return;
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "copy";
    btn.textContent = "Copy";
    btn.addEventListener("click", function () {
      navigator.clipboard.writeText(code.innerText.replace(/\n$/, "")).then(function () {
        btn.textContent = "Copied";
        setTimeout(function () { btn.textContent = "Copy"; }, 1400);
      });
    });
    block.appendChild(btn);
  });

  var menuBtn = document.querySelector(".menu-btn");
  var nav = document.getElementById("sitenav");
  menuBtn.addEventListener("click", function () {
    var open = nav.classList.toggle("open");
    menuBtn.setAttribute("aria-expanded", String(open));
  });

  var root = document.documentElement;
  var themeBtn = document.querySelector(".theme-btn");
  var isDark = function () {
    var t = root.dataset.theme;
    return t ? t === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
  };
  var label = function () { themeBtn.textContent = isDark() ? "Light theme" : "Dark theme"; };
  label();
  themeBtn.addEventListener("click", function () {
    var next = isDark() ? "light" : "dark";
    root.dataset.theme = next;
    try { localStorage.setItem("theme", next); } catch (e) {}
    label();
  });
})();
