# ExKinet — entrega de interfaz para integrar

## Objetivo
Aplicar esta interfaz a la aplicación Android del usuario que ya dispone de un motor de emulación. La referencia visual y de comportamiento es el prototipo incluido; conservar el motor y las funciones que ya funcionan.

Demo publicada: https://orbit-ps2-console.edgarlozado2k.chatgpt.site

## Archivos
- `prototipo/index.html`: código HTML, CSS y JavaScript de la demo.
- `prototipo/assets/`: cinco carátulas y un fondo de ejemplo.
- `logo-exkinet.svg`: logo vectorial azul, extraído del código de la interfaz.
- `PROMPT_PARA_CLAUDE.txt`: instrucciones de integración.
- `ESPECIFICACION.md`: comportamiento y criterios de aceptación.

Para probar: abrir `prototipo/index.html` en un navegador. Para un entorno más consistente, servir la carpeta `prototipo` con un servidor HTTP local. No requiere dependencias ni compilación. Algunas funciones del navegador, incluido almacenamiento, mando o reproducción automática, pueden variar si se abre como archivo local.

## Qué es real en la demo
Selección de juegos, navegación, búsqueda, favoritos, ajustes visuales, exportación/importación de preferencias JSON y selección de vídeos locales con paso de imagen a vídeo. Los ajustes y favoritos se guardan en el navegador; los vídeos son referencias temporales de la sesión, no forman parte de las copias.

## Qué debe conectarse con la aplicación
Ejecutar juegos, configuración real del motor, detección de ROM, paquetes de texturas, guardados de emulación, mandos durante el juego y copias de datos reales. Los botones relacionados con estas funciones son demostraciones; no constituyen una integración de emulación.

## Importante para el desarrollo
- Inspeccionar la tecnología real de la app antes de elegir cómo portar la interfaz.
- Si es nativa, reconstruirla en su sistema de UI; no sustituir automáticamente todo por un WebView.
- Si ya usa HTML/WebView, reutilizar y refactorizar la vista, conectándola con APIs nativas explícitas.
- El prototipo tiene estilos e iteraciones acumulados. Consolidarlos al integrarlo; no copiar todas las reglas superpuestas a ciegas.
- Conservar identidad del paquete, datos y firma de actualización; no incluir claves privadas en la entrega.
- Las carátulas se recortaron de una imagen de referencia pequeña: son ejemplos, no imágenes de alta resolución ni fondos independientes para todos los juegos. Separar portada y fondo en el modelo de datos final.
- No se ha verificado el aspecto ni el rendimiento en una tablet real. Las comprobaciones realizadas cubren sintaxis y parte de la lógica con elementos de interfaz simulados; no son pruebas de navegador ni de Android.
- Las imágenes representan títulos comerciales y se incluyen para la maqueta personal. No asumir derechos de redistribución comercial.
