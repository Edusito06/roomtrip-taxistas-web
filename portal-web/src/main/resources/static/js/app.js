// Mejoras visuales sin build ni framework: solo DOM vanilla sobre HTML servido
// por Thymeleaf. Nada de esto es requerido para que el formulario funcione.
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-file-drop-input]').forEach(function (input) {
    var drop = input.closest('.file-drop');
    var text = drop ? drop.querySelector('[data-file-drop-text] strong') : null;
    var defaultLabel = text ? text.textContent : '';

    input.addEventListener('change', function () {
      if (!drop) return;
      if (input.files && input.files.length > 0) {
        drop.classList.add('has-file');
        drop.querySelector('.file-drop-icon i').className = 'bi bi-check-circle-fill';
        if (text) text.textContent = input.files[0].name;
      } else {
        drop.classList.remove('has-file');
        drop.querySelector('.file-drop-icon i').className = 'bi bi-cloud-arrow-up';
        if (text) text.textContent = defaultLabel;
      }
    });
  });
});

// ---------------------------------------------------------------------
// Carrusel de fotos del panel oscuro (login / registro). Puras fotos de
// taxis peruanos con licencia libre (ver static/img/hero/CREDITOS.md).
// ---------------------------------------------------------------------
(function () {
  var HERO_IMAGES = [
    '/img/hero/taxi-lima-amarillo.jpg',
    '/img/hero/taxi-lima-blanco.jpg',
    '/img/hero/taxi-lima-nuevos.jpg',
    '/img/hero/taxi-cusco.jpg',
    '/img/hero/taxi-arequipa.jpg',
    '/img/hero/mototaxi-chivay.jpg',
    '/img/hero/taxi-van-peru.jpg',
    '/img/hero/taxi-peru-generico.jpg'
  ];
  var INTERVAL_MS = 4000;

  document.addEventListener('DOMContentLoaded', function () {
    var container = document.querySelector('[data-hero-carousel]');
    if (!container) return;
    var slides = container.querySelectorAll('[data-hero-slide]');
    if (slides.length < 2) return;

    // Baraja el orden en cada carga de pagina para que no siempre arranque igual.
    var order = HERO_IMAGES.slice();
    for (var i = order.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = order[i]; order[i] = order[j]; order[j] = tmp;
    }
    order.forEach(function (src) { var img = new Image(); img.src = src; });

    var activeSlide = 0;
    var nextIndex = 1 % order.length;
    slides[0].style.backgroundImage = 'url(' + order[0] + ')';
    slides[0].classList.add('is-active');

    setInterval(function () {
      var idle = (activeSlide + 1) % slides.length;
      slides[idle].style.backgroundImage = 'url(' + order[nextIndex] + ')';
      slides[idle].classList.add('is-active');
      slides[activeSlide].classList.remove('is-active');
      activeSlide = idle;
      nextIndex = (nextIndex + 1) % order.length;
    }, INTERVAL_MS);
  });
})();

// ---------------------------------------------------------------------
// Notificaciones propias del sitio en vez del globo nativo del navegador
// ("Completa este campo"). Un solo toast reutilizable: si el usuario
// reenvia el formulario, se reinicia en lugar de apilarse.
// ---------------------------------------------------------------------
(function () {
  var toastEl = null;
  var hideTimer = null;

  function showToast(message) {
    var container = document.getElementById('app-toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'app-toast-container';
      document.body.appendChild(container);
    }
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'app-alert app-alert-danger app-toast';
      toastEl.setAttribute('role', 'alert');
      toastEl.innerHTML = '<i class="bi bi-exclamation-triangle-fill"></i><span></span>';
      container.appendChild(toastEl);
    }
    toastEl.querySelector('span').textContent = message;

    clearTimeout(hideTimer);
    toastEl.classList.remove('show');
    // Fuerza reflow para que la transicion se note si ya estaba visible.
    void toastEl.offsetWidth;
    toastEl.classList.add('show');
    hideTimer = setTimeout(function () {
      toastEl.classList.remove('show');
    }, 4500);
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('form[data-validate]').forEach(function (form) {
      form.setAttribute('novalidate', 'novalidate');
      form.addEventListener('submit', function (e) {
        if (!form.checkValidity()) {
          e.preventDefault();
          e.stopPropagation();
          form.classList.add('was-validated');
          var firstInvalid = form.querySelector(':invalid');
          if (firstInvalid) {
            firstInvalid.focus();
            firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
          showToast('Completa los campos resaltados en rojo antes de continuar.');
        }
      });
    });
  });
})();

// ---------------------------------------------------------------------
// Paginacion de tablas, en el cliente: no cambia el contrato REST, solo
// reparte en paginas las filas que Thymeleaf ya renderizo.
// ---------------------------------------------------------------------
(function () {
  function paginar(table) {
    var pageSize = parseInt(table.getAttribute('data-page-size'), 10) || 10;
    var tbody = table.querySelector('tbody');
    if (!tbody) return;
    var rows = Array.prototype.slice.call(tbody.querySelectorAll('tr'));
    var nav = document.querySelector('[data-pagination-for="' + table.id + '"]');
    if (rows.length <= pageSize) return; // cabe todo en una pagina, no hace falta nada

    var totalPaginas = Math.ceil(rows.length / pageSize);
    var actual = 1;

    function render() {
      rows.forEach(function (row, i) {
        var pagina = Math.floor(i / pageSize) + 1;
        row.style.display = pagina === actual ? '' : 'none';
      });
      renderControles();
    }

    function irA(pagina) {
      if (pagina < 1 || pagina > totalPaginas || pagina === actual) return;
      actual = pagina;
      render();
      table.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function item(etiqueta, pagina, deshabilitado, activo) {
      var li = document.createElement('li');
      li.className = 'page-item' + (deshabilitado ? ' disabled' : '') + (activo ? ' active' : '');
      var a = document.createElement('a');
      a.className = 'page-link';
      a.href = '#';
      a.textContent = etiqueta;
      if (activo) a.setAttribute('aria-current', 'page');
      a.addEventListener('click', function (e) {
        e.preventDefault();
        irA(pagina);
      });
      li.appendChild(a);
      return li;
    }

    function renderControles() {
      if (!nav) return;
      nav.innerHTML = '';
      var ul = document.createElement('ul');
      ul.className = 'pagination pagination-sm mb-0';
      ul.appendChild(item('Anterior', actual - 1, actual === 1, false));
      for (var p = 1; p <= totalPaginas; p++) {
        ul.appendChild(item(String(p), p, false, p === actual));
      }
      ul.appendChild(item('Siguiente', actual + 1, actual === totalPaginas, false));
      nav.appendChild(ul);
    }

    render();
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('table[data-paginate]').forEach(paginar);
  });
})();

// ---------------------------------------------------------------------
// Ojito para ver u ocultar la contrasena: <button data-toggle-password="id-del-input">
// ---------------------------------------------------------------------
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-toggle-password]').forEach(function (boton) {
    var input = document.getElementById(boton.getAttribute('data-toggle-password'));
    var icono = boton.querySelector('i');
    if (!input) return;
    boton.addEventListener('click', function () {
      var mostrar = input.type === 'password';
      input.type = mostrar ? 'text' : 'password';
      boton.setAttribute('aria-pressed', String(mostrar));
      boton.setAttribute('aria-label', mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña');
      boton.title = mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña';
      if (icono) icono.className = mostrar ? 'bi bi-eye-slash text-muted' : 'bi bi-eye text-muted';
      input.focus();
    });
  });
});
