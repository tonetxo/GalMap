# Especificación e Plan de Implementación: App Android de Realidade Aumentada Xeográfica

## 1. Análise e Recomendación de Orixes de Datos (Data Sources)

Para unha aplicación de Realidade Aumentada (AR) baseada en localización (tipo *PeakFinder* ou *AR Alpine*), necesitas dous tipos de datos fundamentais: **Puntos de Interese Xeográficos (POIs)** (nome, tipo, latitude, lonxitude, altitude/prominencia) e, opcionalmente, **Modelos Dixitais de Elevación (DEM)** para calcular a oclusión (saber se unha montaña tapa a outra).

Aquí tes a comparativa das fontes que mencionas e a recomendación arquitectónica:

### A. OpenStreetMap (vía Overpass API) - ⭐ RECOMENDADO PARA COMEZAR (Global e Rápido)
*   **Vantaxes:** É gratuíto, ten unha API moi potente (Overpass QL) que permite consultar exactamente o que queres nun raio determinado (ex: todos os `natural=peak`, `natural=cape`, `place=island` a 15 km á redonda). Ademais, en Galicia a comunidade de OSM ten un nivel de detalle excelente e moitos picos teñen a etiqueta `ele` (elevación) e `wikidata`.
*   **Desvantaxes:** Algúns accidentes secundarios poden non ter o dato de altitude (`ele`) rexistrado.

### B. Instituto Geográfico Nacional (IGN) / CNIG - ⭐ MELLOR PRECISIÓN EN ESPAÑA
*   **Produto clave:** **Nomenclátor Geográfico Básico de España (NGBE)** e os **Modelos Dixitais do Terreo (MDT05 / MDT25)**.
*   **Vantaxes:** Datos oficiais, altitudes exactas e clasificación rigorosa de orografía e hidrografía.
*   **Desvantaxes:** As súas APIs en tempo real (servizos WFS do IDEE) son lentas e pesadas (devolven XML/GML complexos) para consultalas directamente dende un móbil en movemento.
*   **Uso ideal:** Descargar o Nomenclátor, filtralo cun script en Python (quedándote con picos, illas, cabos, ríos) e convertelo nunha base de datos **SQLite/Room** preempaquetada na app ou aloxada nun servidor propio (ex. Supabase/Firebase).

### C. Xunta de Galicia (IET - Instituto de Estudos do Territorio) - ⭐ MELLOR PARA TOPONIMIA GALEGA
*   **Produto clave:** **Proxecto Toponimia de Galicia** e servizos WFS de Información Xeográfica da Xunta (mapas.xunta.gal).
*   **Vantaxes:** A mellor fonte para microtoponimia, nomes oficiais en galego sen deturpar, cabos locais, illotes (ex. nas Rías Baixas) e regatos.
*   **Desvantaxes:** Ao igual que o IGN, os servizos WFS non están optimizados para consultas REST rápidas dende cliente móbil. Recoméndase descarga previa e procesamento.

### D. Google Maps Platform (Places API / Elevation API) - ❌ NON RECOMENDADO PARA ISTO
*   **Desvantaxes:** A Places API está orientada a negocios e turismo, non a orografía (faltan moitísimos picos, cabos e accidentes naturais). Ademais, o custo por petición é moi alto. O único servizo útil sería a *Elevation API*, pero hai alternativas gratuítas (como Open-Elevation ou ficheiros HGT locais).

---

### 💡 A Estratexia de Datos Gañadora (Arquitectura Híbrida)
Para que o teu axente de código constrúa un MVP (Produto Mínimo Viable) funcional hoxe mesmo sen ter que montar un servidor propio:
1.  **Fase 1 (MVP):** Usar **Overpass API (OpenStreetMap)** directamente dende a app. Cunha simple petición HTTP obtés un JSON limpo cos picos, cabos e illas ao redor do usuario.
2.  **Fase 2 (Mellora):** Se algún punto non ten altitude en OSM, consultar a API gratuíta **Open-Elevation** ou **OpenTopoData**.
3.  **Fase 3 (Produción Galicia/España):** Substituír ou combinar a chamada á API por unha base de datos **SQLite (Room)** offline xerada a partir dos datos abertos do **IGN + Xunta de Galicia**.

---
---

## 2. Plan de Implementación para o Axente de Código

> **Instrucións para o usuario:** Copia o contido a partir de aquí (ou pásalle este ficheiro `.md` enteiro) ao teu axente de código (Cursor, Claude Code, GitHub Copilot, etc.) co seguinte prompt: *"Actúa como un desenvolvedor Senior de Android. Le este documento de especificacións e imos implementar a aplicación paso a paso comezando pola Fase 1."*

---

### Especificación Técnica do Proxecto (Contexto para o Axente IA)

#### Obxectivo do Proxecto
Desenvolver unha aplicación nativa para Android en Kotlin que utilice a cámara do dispositivo, o GPS e os sensores de orientación (xiroscopio, acelerómetro, magnetómetro) para superpoñer en Realidade Aumentada (AR basada en localización) etiquetas cos accidentes xeográficos próximos (montañas/picos, illas, cabos, ríos).

#### Stack Tecnolóxico Requirido
*   **Linguaxe:** Kotlin (100%).
*   **UI Framework:** Jetpack Compose (Material 3) para a interface e para renderizar o *overlay* 2D de etiquetas AR sobre o *feed* da cámara.
*   **Cámara:** `CameraX` (`PreviewView`) para mostrar a imaxe en tempo real de forma eficiente (non é necesario ARCore completo para AR xeodésica a longa distancia, xa que ARCore está pensado para planos a curta distancia; o uso de sensores + proxección matemática + CameraX é máis lixeiro, compatible con máis dispositivos e consome menos batería).
*   **Localización:** `Google Play Services Location` (`FusedLocationProviderClient`).
*   **Sensores:** `SensorManager` (`TYPE_ROTATION_VECTOR` para obter a matriz de rotación e calcular Azimut, Pitch e Roll).
*   **Rede:** `Retrofit2` + `OkHttp` + `Kotlinx Serialization` (ou `Gson`) para consultar a **Overpass API** (OpenStreetMap).
*   **Caché Local:** `Room Database` para gardar os puntos descargados e non saturar a API se o usuario non se moveu máis de $X$ quilómetros.
*   **Arquitectura:** MVVM (Model-View-ViewModel) con `StateFlow` e Coroutines.

---

### Matemáticas e Lóxica de Proxección AR

O axente de código debe implementar unha clase utilitaria `GeoArMath` coas seguintes fórmulas:

1.  **Cálculo de Distancia (Fórmula de Haversine):**
    Dados $(\phi_1, \lambda_1)$ (usuario) e $(\phi_2, \lambda_2)$ (accidente xeográfico) en radiáns, e o raio da Terra $R \approx 6371000\text{ m}$:
    $$a = \sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1) \cdot \cos(\phi_2) \cdot \sin^2\left(\frac{\Delta\lambda}{2}\right)$$
    $$c = 2 \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1-a}\right)$$
    $$d = R \cdot c$$

2.  **Cálculo do Rumbo / Azimut ($\theta$ - Bearing):**
    $$\theta = \text{atan2}\left(\sin(\Delta\lambda) \cdot \cos(\phi_2), \cos(\phi_1) \cdot \sin(\phi_2) - \sin(\phi_1) \cdot \cos(\phi_2) \cdot \cos(\Delta\lambda)\right)$$
    *(Converter a graos e normalizar no rango $[0^\circ, 360^\circ)$).*

3.  **Cálculo do Ángulo de Elevación ($\alpha$ - Pitch do obxectivo):**
    Dada a altitude do usuario $h_{\text{user}}$ e a altitude do pico $h_{\text{target}}$, tendo en conta unha corrección básica pola curvatura terrestre:
    $$\Delta h = (h_{\text{target}} - h_{\text{user}}) - \frac{d^2}{2R}$$
    $$\alpha = \text{atan2}(\Delta h, d)$$

4.  **Proxección en Pantalla $(X, Y)$:**
    *   Obter a orientación actual do teléfono mediante `SensorManager.getRotationMatrixFromVector` e `SensorManager.getOrientation` (remapeando os eixos con `remapCoordinateSystem` para cando o móbil está en posición vertical/retrato ou apaisado).
    *   Calcular a diferenza angular horizontal ($\Delta\text{Azimut}$) e vertical ($\Delta\text{Pitch}$) entre cara a onde mira a cámara e onde está o punto xeográfico.
    *   Asumindo un campo de visión da cámara ($\text{FOV}_h \approx 60^\circ$, $\text{FOV}_v \approx 45^\circ$ ou obtido dinamicamente de `Camera2Interop`):
        $$X_{\text{screen}} = \frac{\text{Width}}{2} + \left(\frac{\Delta\text{Azimut}}{\text{FOV}_h / 2}\right) \cdot \frac{\text{Width}}{2}$$
        $$Y_{\text{screen}} = \frac{\text{Height}}{2} - \left(\frac{\Delta\text{Pitch}}{\text{FOV}_v / 2}\right) \cdot \frac{\text{Height}}{2}$$
    *   *Filtro de Suavizado (Low-Pass Filter):* Aplicar un filtro exponencial ás lecturas do sensor para evitar que as etiquetas treman na pantalla:
        $$v_{\text{filtrado}} = v_{\text{anterior}} + \beta \cdot (v_{\text{actual}} - v_{\text{anterior}})$$ (onde $\beta \approx 0.15$).

---

### Consulta á API (Overpass QL)

A app consultará o endpoint `https://overpass-api.de/api/interpreter` enviando un `POST` co seguinte corpo (substituíndo `RAD`, `LAT`, `LON`, por exemplo `RAD = 20000` metros):

```overpassql
[out:json][timeout:25];
(
  node["natural"="peak"](around:RAD,LAT,LON);
  node["natural"="volcano"](around:RAD,LAT,LON);
  node["natural"="cape"](around:RAD,LAT,LON);
  node["natural"="saddle"](around:RAD,LAT,LON);
  node["place"="island"](around:RAD,LAT,LON);
  way["place"="island"](around:RAD,LAT,LON);
  node["place"="islet"](around:RAD,LAT,LON);
  node["waterway"="waterfall"](around:RAD,LAT,LON);
);
out center body;
```
*(Nota: `out center` asegura que se unha illa está debuxada como un polígono/`way`, Overpass devolva un único punto central coas súas coordenadas).*

---

### Fases de Desenvolvemento (Roadmap para o Axente)

#### Fase 1: Configuración do Proxecto e Permisos
1. Configurar `build.gradle.kts` coas dependencias: Jetpack Compose, CameraX, Play Services Location, Retrofit2, Coroutines, Lifecycle ViewModel.
2. Configurar `AndroidManifest.xml` cos permisos: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`.
3. Crear unha pantalla inicial en Compose que solicite os permisos de Cámara e Localización en tempo de execución antes de mostrar o visor AR.

#### Fase 2: Motor de Sensores e Localización
1. Crear `LocationTracker`: Unha clase que emita un `Flow<Location>` coa posición GPS e a altitude do usuario.
2. Crear `OrientationSensorTracker`: Unha clase que escoite o `TYPE_ROTATION_VECTOR`, aplique `remapCoordinateSystem` (para que funcione co teléfono ergueito mirando ao horizonte) e emita un `Flow<DeviceOrientation>` (Azimut en graos $[0, 360)$, Pitch en graos $[-90, 90]$, Roll) aplicando un filtro de paso baixo (Low-Pass Filter) para evitar vibracións.

#### Fase 3: Capa de Datos (Overpass API)
1. Definir o modelo de datos `GeoFeature` (`id`, `name`, `type`, `lat`, `lon`, `elevation`).
2. Configurar Retrofit para chamar á Overpass API.
3. Mapear a resposta JSON de Overpass: filtrar elementos que non teñan a etiqueta `name` (ignorar puntos anónimos). Dar prioridade á etiqueta `name:gl` (nome en galego) se existe; se non, usar `name`. Extraer a etiqueta `ele` (elevación) limpando caracteres non numéricos. Se un cabo ou illa non ten `ele`, asignar `0.0` (nivel do mar).

#### Fase 4: Matemáticas AR e ViewModel
1. Implementar `GeoArMath` cos cálculos de Haversine, Bearing, Pitch relativo e proxección a coordenadas de pantalla.
2. No `ArViewModel`, combinar os fluxos de `Location`, `DeviceOrientation` e a lista de `GeoFeature` para emitir unha lista de `ArScreenItem` (que contén o nome, tipo, distancia en km, altitude e posición $X, Y$ normalizada en pantalla).
3. Filtrar os elementos que estean fóra do campo de visión actual (ex. $|\Delta\text{Azimut}| > 45^\circ$).

#### Fase 5: Interface de Usuario AR (CameraX + Compose Canvas)
1. Implementar `CameraPreview` usando `AndroidView` e `PreviewView` de CameraX a pantalla completa.
2. Superpoñer un `Box` / `Canvas` de Jetpack Compose por riba da cámara.
3. Debuxar cada `ArScreenItem`:
    *   Unha liña vertical ou marcador apuntando á coordenada exacta.
    *   Unha tarxeta semitransparente (estilo *chip*) cunha icona segundo o tipo (⛰️ Pico, 🏝️ Illa, 🌊 Cabo), o nome do accidente xeográfico, a súa altitude (se é $> 0$) e a distancia en km.
    *   **Xestión de colisións (Anti-solapamento):** Se dúas etiquetas están moi xuntas no eixe $X$, desprazar verticalmente (escalonar no eixe $Y$) as etiquetas dos elementos máis afastados para que sexan todas lexibles.
4. Engadir un pequeno radar ou compás na esquina superior e un *slider* para cambiar o raio de busca (ex. de 5 km a 40 km).