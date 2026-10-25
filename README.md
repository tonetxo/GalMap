# GalMap

App Android de **realidade aumentada xeográfica**: apunta a cámara á paisaxe e GalMap
superpón os nomes dos montes, colados, illas, cabos e fervenzas que tes diante, coa
distancia e a altitude. Pensada para Galicia, funciona sen conexión grazas a un dataset
empaquetado, e en calquera outro lugar descargando os datos de OpenStreetMap ao momento.

## Funcionalidades

- **Etiquetas AR sobre a cámara** (CameraX + Jetpack Compose), con rumbo, distancia e altitude.
- **Orientación estable**: fusión propia de xiroscopio e compás que detecta interferencias
  magnéticas e mantén o rumbo co xiroscopio mentres duran.
- **Calibración do compás**: toca unha etiqueta e aliñala co lugar real, ou arrastra en horizontal.
- **Filtros** por tipo de lugar e por importancia (prominencia aproximada dos montes,
  lugares con artigo na Wikipedia como principais).
- **Dataset offline de Galicia** (~9.200 lugares, 225 KB) máis unha marxe de ~40 km cara a
  Asturias, León, Zamora e Portugal. Fóra desa zona consúltase a Overpass API.
- **Ver no mapa**: abre Google Earth en 3D para lugares con Wikipedia, ou unha ligazón
  `geo:` coas coordenadas exactas para o resto.
- Interface en galego e inglés, vertical e horizontal.

## Requisitos

- Android 8.0 (API 26) ou superior, con cámara, GPS e sensores de orientación
  (acelerómetro, magnetómetro e, idealmente, xiroscopio).
- Para compilar: JDK 17 e Android SDK 36.

## Compilar e instalar

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
./gradlew installDebug           # instala no dispositivo conectado
./gradlew test                   # tests unitarios
```

### APK sen compilar en local

Cada push a `main` pasa os tests e compila o APK en GitHub Actions. O APK descárgase
como artefacto (`galmap-debug-…`) desde a execución correspondente na pestana
[Actions](https://github.com/tonetxo/GalMap/actions). Tamén se pode lanzar a man con
«Run workflow».

## Rexenerar o dataset offline

O dataset descárgase de OpenStreetMap por teselas mediante a Overpass API e gárdase en
`app/src/main/assets/` co mesmo formato que as respostas en vivo:

```bash
python3 tools/build_dataset.py
```

As teselas descargadas quedan en caché en `build/dataset-tiles/`, así que unha execución
interrompida continúa onde quedou.

## Estrutura

```
app/src/main/java/com/tonetxo/galmap/
├── ar/        Matemáticas xeodésicas (distancia, rumbo, elevación) e proxección en pantalla
├── data/      Modelo, cliente e parser de Overpass, dataset empaquetado, ranking de importancia
├── sensors/   Localización (Fused Location) e orientación (fusión xiroscopio + compás)
└── ui/        Pantalla AR, overlay de etiquetas, cinta do compás, axustes e permisos
tools/         Script para xerar o dataset offline
```

## Licenza

O código publícase baixo a [licenza MIT](LICENSE).

Os datos xeográficos (incluído `app/src/main/assets/dataset.json.gz`) proceden de
[OpenStreetMap](https://www.openstreetmap.org/copyright) e están dispoñibles baixo a
[Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).
© Colaboradores de OpenStreetMap.
