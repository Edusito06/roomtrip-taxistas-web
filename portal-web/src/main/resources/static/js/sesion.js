// Al volver con las flechas "atras" / "adelante" el navegador puede mostrar la pagina desde su
// cache de ida y vuelta (bfcache) sin preguntarle nada al servidor: se vería el panel de una
// sesion ya cerrada. Si la pagina viene de esa cache, se recarga para que el servidor decida
// (con la sesion cerrada redirige al login).
window.addEventListener('pageshow', function (evento) {
  var navegacion = performance.getEntriesByType && performance.getEntriesByType('navigation')[0];
  var deHistorial = navegacion && navegacion.type === 'back_forward';
  if (evento.persisted || deHistorial) {
    window.location.reload();
  }
});
