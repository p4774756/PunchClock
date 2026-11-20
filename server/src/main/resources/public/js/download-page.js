(function () {
  const available = window.PUNCHCLOCK_CLIENT_AVAILABLE === true;
  const jar = document.querySelector('[data-download-jar]');
  const missing = document.querySelector('[data-download-missing]');
  if (available && jar) jar.hidden = false;
  if (!available && missing) missing.hidden = false;
})();