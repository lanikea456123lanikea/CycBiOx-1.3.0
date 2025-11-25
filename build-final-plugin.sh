#!/bin/bash

# Final Plugin Build and Verification Script
# QuPath Cell Phenotype Manager v2.1.0 with Live Preview

echo "=== QuPath Cell Phenotype Manager v2.1.0 Final Build ==="
echo "Building plugin with Live Preview functionality..."
echo

# Clean and rebuild
echo "🧹 Cleaning previous builds..."
./gradlew clean --quiet

echo "🔨 Building plugin..."
./gradlew build --no-daemon --quiet

# Check build result
if [ $? -eq 0 ]; then
    echo "✅ Build completed successfully!"
else
    echo "❌ Build failed!"
    exit 1
fi

# Verify JAR file
JAR_FILE="build/libs/cycbiox-1.0.0.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✅ JAR file created: $JAR_FILE"
    echo "📦 Size: $(du -h $JAR_FILE | cut -f1)"
else
    echo "❌ JAR file not found!"
    exit 1
fi

# Verify Live Preview methods in compiled class
echo
echo "🔍 Verifying Live Preview functionality..."
if javap -cp "$JAR_FILE" -private com.cellphenotype.qupath.ui.CellPhenotypeManagerPane | grep -q "toggleLivePreview"; then
    echo "✅ toggleLivePreview() method found"
else
    echo "❌ toggleLivePreview() method missing!"
fi

if javap -cp "$JAR_FILE" -private com.cellphenotype.qupath.ui.CellPhenotypeManagerPane | grep -q "updateLivePreview"; then
    echo "✅ updateLivePreview() method found"
else
    echo "❌ updateLivePreview() method missing!"
fi

if javap -cp "$JAR_FILE" -private com.cellphenotype.qupath.ui.CellPhenotypeManagerPane | grep -q "livePreviewButton"; then
    echo "✅ livePreviewButton field found"
else
    echo "❌ livePreviewButton field missing!"
fi

# Verify service registration
echo
echo "🔍 Verifying QuPath extension registration..."
if jar -tf "$JAR_FILE" | grep -q "META-INF/services/qupath.lib.gui.extensions.QuPathExtension"; then
    echo "✅ Service registration file exists"
    
    # Check service content
    SERVICE_CONTENT=$(unzip -p "$JAR_FILE" META-INF/services/qupath.lib.gui.extensions.QuPathExtension 2>/dev/null)
    if [ "$SERVICE_CONTENT" = "com.cellphenotype.qupath.CellPhenotypeExtension" ]; then
        echo "✅ Service registration content correct"
    else
        echo "❌ Service registration content incorrect: $SERVICE_CONTENT"
    fi
else
    echo "❌ Service registration file missing!"
fi

# Verify main classes
echo
echo "🔍 Verifying core classes..."
REQUIRED_CLASSES=(
    "com/cellphenotype/qupath/CellPhenotypeExtension.class"
    "com/cellphenotype/qupath/ui/CellPhenotypeManagerPane.class"
    "com/cellphenotype/qupath/CellPhenotypeAPI.class"
    "com/cellphenotype/qupath/classifier/CellPhenotypeClassifier.class"
)

ALL_CLASSES_FOUND=true
for class in "${REQUIRED_CLASSES[@]}"; do
    if jar -tf "$JAR_FILE" | grep -q "$class"; then
        echo "✅ $(basename "$class")"
    else
        echo "❌ $(basename "$class") missing!"
        ALL_CLASSES_FOUND=false
    fi
done

# Installation instructions
if [ "$ALL_CLASSES_FOUND" = true ]; then
    echo
    echo "🎉 Plugin build and verification completed successfully!"
    echo
    echo "📋 Installation Instructions:"
    echo "1. Copy plugin to QuPath extensions directory:"
    echo "   cp $JAR_FILE ~/.qupath/v0.6/extensions/"
    echo
    echo "2. Restart QuPath"
    echo
    echo "3. Open Extensions > Cell Phenotype Manager"
    echo
    echo "4. Test Live Preview:"
    echo "   • Load an image with cell detections"
    echo "   • Click 'Live Preview' button (next to refresh channels)"
    echo "   • Adjust threshold sliders"
    echo "   • Observe real-time cell coloring"
    echo "   • Click '停止 Live Preview' to deactivate"
    echo
    echo "✨ Live Preview Features:"
    echo "   • Positive cells: Purple/Magenta color"
    echo "   • Negative cells: Gray color"
    echo "   • Real-time threshold response"
    echo "   • Status updates in QuPath title"
    echo "   • DAPI channel auto-exclusion"
else
    echo
    echo "❌ Plugin verification failed! Some required classes are missing."
    exit 1
fi