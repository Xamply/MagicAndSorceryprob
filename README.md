# Magic and Sorcery (Minecraft 1.20.1 - Forge 47.2.23)

Mod de magia y hechicería para Minecraft Java Edition 1.20.1 usando Minecraft Forge.

---

## 📋 Requisitos del Entorno

- **Minecraft**: 1.20.1
- **Forge**: 47.2.23
- **Java**: JDK 17 (configurado automáticamente en `.jdks\jdk-17.0.20.1+1`)
- **Gradle**: 8.1.1 (vía Gradle Wrapper `./gradlew.bat`)

---

## 🚀 Comandos Principales

Ejecuta estos comandos desde la consola PowerShell en la raíz del proyecto:

### 1. Compilar y empaquetar el Mod (`.jar`)
```powershell
.\gradlew.bat build
```
El archivo JAR generado se guardará en `build/libs/magic_and_sorcery-1.0.0.jar`.

### 2. Probar el Mod en Minecraft (Cliente)
```powershell
.\gradlew.bat runClient
```
Inicia una sesión de desarrollo de Minecraft con el mod cargado para probar los ítems, bloques y mecánicas directamente en el juego.

### 3. Probar en Servidor Dedicado
```powershell
.\gradlew.bat runServer
```

---

## 🔮 Contenido Inicial Registrado

- **Pestaña Creativa**: `Magia y Hechicería` (`itemGroup.magic_and_sorcery`)
- **Ítems**:
  - `magic_crystal`: Cristal Mágico (Rareza RARA)
  - `magic_wand`: Varita Mágica (Rareza ÉPICA, no acumulable)
  - `spell_tome`: Tomo de Hechizos (Rareza POCO COMÚN, no acumulable)
- **Bloques**:
  - `sorcery_stone`: Piedra de Hechicería con vetas mágicas luminosas

---

## 📂 Estructura del Código

```
src/main/
├── java/com/cesar/magicandsorcery/
│   ├── MagicAndSorcery.java              # Clase principal (@Mod)
│   ├── block/
│   │   └── ModBlocks.java                # Registro de bloques
│   └── item/
│       ├── ModItems.java                 # Registro de ítems
│       └── ModCreativeModeTabs.java      # Pestaña del modo creativo
└── resources/
    ├── META-INF/
    │   └── mods.toml                     # Metadatos del mod
    ├── pack.mcmeta                       # Información de recursos
    └── assets/magic_and_sorcery/
        ├── blockstates/                  # Estados de bloques
        ├── lang/                         # Traducciones (es_es.json, en_us.json)
        ├── models/
        │   ├── block/                    # Modelos 3D de bloques
        │   └── item/                     # Modelos de ítems
        └── textures/
            ├── block/                    # Texturas PNG de bloques
            └── item/                     # Texturas PNG de ítems
```

---

## 🛠️ Cómo agregar nuevo contenido

1. **Nuevo Ítem**:
   - Agrega la definición en `com.cesar.magicandsorcery.item.ModItems`.
   - Agrega su modelo en `assets/magic_and_sorcery/models/item/<item_id>.json`.
   - Coloca su textura PNG de 16x16 en `assets/magic_and_sorcery/textures/item/<item_id>.png`.
   - Agrega su traducción en `lang/en_us.json` y `lang/es_es.json`.
   - Añádelo a la pestaña creativa en `ModCreativeModeTabs`.

2. **Nuevo Bloque**:
   - Agrega la definición en `com.cesar.magicandsorcery.block.ModBlocks`.
   - Agrega `blockstates/<block_id>.json`, `models/block/<block_id>.json` y `models/item/<block_id>.json`.
   - Coloca la textura en `textures/block/<block_id>.png`.
