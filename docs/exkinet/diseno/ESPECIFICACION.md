# Diseño y aceptación

## Apariencia
- Fondo base aproximado #050A10; blanco en títulos; texto secundario gris azulado.
- Acento azul eléctrico: #0871F9 a #299AFF; borde luminoso cian #84DDFF.
- Logo X vectorial y nombre ExKinet. No usar la marca anterior ORBIT en la interfaz final.
- Inicio: navegación superior, presentación del juego a la izquierda, imagen cinematográfica de fondo y colección inferior.
- No existe un panel rectangular opaco cortando el fondo: usar un degradado continuo, con orden de capas explícito.
- Esquinas de las tarjetas suavizadas, aproximadamente 13 px en el prototipo; escalar según densidad.

## Movimiento de referencia
- Fundido entre fondos: aproximadamente 800 ms, sin pantalla vacía intermedia.
- Tarjeta seleccionada: elevación máxima de unos 16 px, asentamiento en unos -6 px, escala final aproximada 1.035; duración cercana a 650 ms.
- Curva de referencia cubic-bezier(.22,.75,.18,1). Adaptar al lenguaje nativo, no exigir identidad numérica si el resultado necesita ajuste.
- Mantener los elementos y sus identificadores: cambiar estado no debe desmontar y reconstruir todas las tarjetas.
- Desplazamiento horizontal suave cuando la tarjeta quede fuera de la zona visible.
- El borde iluminado no debe quedar cortado por el contenedor.
- Reducir movimiento desactiva animaciones y vídeos automáticos.

## Datos por juego
ID estable, nombre, descripción, ubicación del juego, portada, fondo independiente, vídeo opcional, favorito y ajustes reales del motor. No confundir estados de ejemplo con partidas reales.

## Vídeo
Imagen inicial; espera configurable de 2, 3 o 5 segundos; vídeo opcional sin sonido y en bucle. Comprobar formato compatible antes de activar. Volver a imagen si falla. Liberar reproductores, temporizadores y recursos al cambiar de juego, entrar en otra sección, iniciar emulación o pasar a segundo plano. En Android usar almacenamiento persistente y permisos adecuados; la demo usa URLs temporales solo por ser un prototipo.

## Pantalla de Ajustes
General, Apariencia y movimiento, Gráficos por juego, Fondos y vídeos, Texturas HD, Controles, Copias y restauración. Incluir vuelta clara a Inicio. No mostrar funciones como operativas si el motor no las soporta.

## Pruebas de aceptación para la app final
1. Cambiar repetidamente entre los cinco juegos sin saltos, imágenes atrasadas ni bloqueo táctil.
2. Comprobar que el fondo no presenta la antigua división vertical en tablet horizontal.
3. Comprobar el brillo en ambas tarjetas extremas y la elevación sin recortes.
4. Navegar con tacto y mando, abrir Ajustes y volver conservando selección.
5. Vídeo asignado: imagen inicial, espera y fundido; cambio rápido cancela el vídeo anterior; juego sin vídeo conserva imagen.
6. Reiniciar la app y verificar favoritos, ajustes y referencias de medios.
7. Verificar reducción de movimiento y comportamiento en segundo plano.
8. Iniciar y cerrar un juego real conservando el funcionamiento del motor y los guardados.
9. Exportar/restaurar una copia real y comprobar sus datos, sin incluir claves de firma.

## Limitaciones del material entregado
No incluye motor de PS2, ROM, BIOS, vídeos, paquetes HD ni partidas reales. Las imágenes son recortes de referencia de resolución limitada. La demo no ha sido validada visualmente en navegador ni en dispositivo Android real.
