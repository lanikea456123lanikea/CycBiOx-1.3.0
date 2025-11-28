/**
 * QuPath Cell Phenotype Manager - Example Groovy Script
 * 
 * This script demonstrates how to use the Cell Phenotype Manager API
 * for automated cell phenotype classification.
 * 
 * Instructions:
 * 1. Load an image with detected cells in QuPath
 * 2. Install the Cell Phenotype Manager plugin
 * 3. Run this script in the Script Editor
 */

import com.cellphenotype.qupath.CellPhenotypeAPI
import com.cellphenotype.qupath.model.CellPhenotype
import com.cellphenotype.qupath.model.ThresholdConfig
import com.cellphenotype.qupath.classifier.CellPhenotypeClassifier

// Get current image data
def imageData = getCurrentImageData()
if (imageData == null) {
    println "Please open an image first!"
    return
}

println "=== QuPath Cell Phenotype Manager Demo ==="
println "Image: ${imageData.getServer().getMetadata().getName()}"

// Get detected objects
def hierarchy = imageData.getHierarchy()
def detectedObjects = hierarchy.getDetectionObjects()
println "Found ${detectedObjects.size()} detected objects"

if (detectedObjects.isEmpty()) {
    println "Please run cell detection first!"
    return
}

// Create threshold configuration
def config = CellPhenotypeAPI.createThresholdConfig("Demo Configuration")
config = config.withSavePath("/tmp")
config = config.withStrategy(ThresholdConfig.Strategy.MANUAL)

println "\n=== Setting up threshold configuration ==="

// Configure thresholds for different markers
// These values should be adjusted based on your specific staining and imaging conditions
def markers = [
    "marker1": ["Nucleus: Mean", 150.0],  // e.g., CD4
    "marker2": ["Nucleus: Mean", 100.0],  // e.g., CD3
    "marker3": ["Nucleus: Mean", 120.0],  // e.g., CD8
    "marker4": ["Nucleus: Mean", 80.0],   // e.g., CD19
    "marker5": ["Nucleus: Mean", 200.0]   // e.g., Custom marker
]

markers.each { markerName, params ->
    def threshold = new ThresholdConfig.ChannelThreshold(params[0], params[1], true)
    config = config.withChannelThreshold(markerName, threshold)
    println "Set threshold for ${markerName}: ${params[0]} > ${params[1]}"
}

// Create phenotype definitions
def phenotypes = []

println "\n=== Creating phenotype definitions ==="

// Helper T Cell (CD4+ CD3+ CD8-)
def helperTCell = CellPhenotypeAPI.createPhenotype("Helper T Cell", 100)
helperTCell = helperTCell.withMarkerState("marker1", CellPhenotype.MarkerState.POSITIVE)  // CD4
helperTCell = helperTCell.withMarkerState("marker2", CellPhenotype.MarkerState.POSITIVE)  // CD3
helperTCell = helperTCell.withMarkerState("marker3", CellPhenotype.MarkerState.NEGATIVE)  // CD8
helperTCell = helperTCell.withMarkerState("marker4", CellPhenotype.MarkerState.IGNORE)
helperTCell = helperTCell.withMarkerState("marker5", CellPhenotype.MarkerState.IGNORE)
phenotypes.add(helperTCell)
println "Created phenotype: Helper T Cell (priority: 100)"

// Cytotoxic T Cell (CD4- CD3+ CD8+)
def cytotoxicTCell = CellPhenotypeAPI.createPhenotype("Cytotoxic T Cell", 90)
cytotoxicTCell = cytotoxicTCell.withMarkerState("marker1", CellPhenotype.MarkerState.NEGATIVE) // CD4
cytotoxicTCell = cytotoxicTCell.withMarkerState("marker2", CellPhenotype.MarkerState.POSITIVE) // CD3
cytotoxicTCell = cytotoxicTCell.withMarkerState("marker3", CellPhenotype.MarkerState.POSITIVE) // CD8
cytotoxicTCell = cytotoxicTCell.withMarkerState("marker4", CellPhenotype.MarkerState.IGNORE)
cytotoxicTCell = cytotoxicTCell.withMarkerState("marker5", CellPhenotype.MarkerState.IGNORE)
phenotypes.add(cytotoxicTCell)
println "Created phenotype: Cytotoxic T Cell (priority: 90)"

// B Cell (CD4- CD3- CD8- CD19+)
def bCell = CellPhenotypeAPI.createPhenotype("B Cell", 80)
bCell = bCell.withMarkerState("marker1", CellPhenotype.MarkerState.NEGATIVE) // CD4
bCell = bCell.withMarkerState("marker2", CellPhenotype.MarkerState.NEGATIVE) // CD3
bCell = bCell.withMarkerState("marker3", CellPhenotype.MarkerState.NEGATIVE) // CD8
bCell = bCell.withMarkerState("marker4", CellPhenotype.MarkerState.POSITIVE) // CD19
bCell = bCell.withMarkerState("marker5", CellPhenotype.MarkerState.IGNORE)
phenotypes.add(bCell)
println "Created phenotype: B Cell (priority: 80)"

// T Cell (CD3+ regardless of CD4/CD8)
def tCell = CellPhenotypeAPI.createPhenotype("T Cell", 70)
tCell = tCell.withMarkerState("marker1", CellPhenotype.MarkerState.IGNORE)   // CD4
tCell = tCell.withMarkerState("marker2", CellPhenotype.MarkerState.POSITIVE) // CD3
tCell = tCell.withMarkerState("marker3", CellPhenotype.MarkerState.IGNORE)   // CD8
tCell = tCell.withMarkerState("marker4", CellPhenotype.MarkerState.IGNORE)
tCell = tCell.withMarkerState("marker5", CellPhenotype.MarkerState.IGNORE)
phenotypes.add(tCell)
println "Created phenotype: T Cell (priority: 70)"

// Perform cell classification
println "\n=== Running cell phenotype classification ==="
def startTime = System.currentTimeMillis()

def results = CellPhenotypeAPI.classifyCells(imageData, config, phenotypes)

def endTime = System.currentTimeMillis()
println "Classification completed in ${(endTime - startTime)} ms"
println "Classified ${results.size()} cells"

// Analyze results
def phenotypeCounts = [:]
def totalPositiveProteins = 0

results.each { result ->
    def phenotypeName = result.getPhenotypeName()
    phenotypeCounts[phenotypeName] = (phenotypeCounts[phenotypeName] ?: 0) + 1
    
    def proteinCount = result.getPositiveProteins().split(",").findAll { it.trim() }.size()
    totalPositiveProteins += proteinCount
}

println "\n=== Classification Results ==="
phenotypeCounts.each { phenotype, count ->
    def percentage = String.format("%.1f", (count * 100.0) / results.size())
    println "${phenotype}: ${count} cells (${percentage}%)"
}

def avgPositiveProteins = totalPositiveProteins / results.size()
println "Average positive proteins per cell: ${String.format('%.1f', avgPositiveProteins)}"

// Update the display
fireHierarchyUpdate()
println "\nCell classifications have been applied to the image display."

// Export results to CSV
def csvFile = new File("/tmp", "cell_phenotype_demo_results.csv")
try {
    CellPhenotypeAPI.exportToCSV(results, csvFile)
    println "Results exported to: ${csvFile.getAbsolutePath()}"
} catch (Exception e) {
    println "Export failed: ${e.getMessage()}"
    println "Trying alternative export location..."
    
    // Try user home directory
    def homeDir = System.getProperty("user.home")
    csvFile = new File(homeDir, "cell_phenotype_demo_results.csv")
    try {
        CellPhenotypeAPI.exportToCSV(results, csvFile)
        println "Results exported to: ${csvFile.getAbsolutePath()}"
    } catch (Exception e2) {
        println "Alternative export also failed: ${e2.getMessage()}"
    }
}

// Save configuration
def configFile = new File("/tmp", "demo_phenotype_config.json")
try {
    CellPhenotypeAPI.saveConfiguration(config, phenotypes, configFile)
    println "Configuration saved to: ${configFile.getAbsolutePath()}"
} catch (Exception e) {
    println "Configuration save failed: ${e.getMessage()}"
}

println "\n=== Demo completed ==="
println "Check the Results table in QuPath for detailed cell measurements."
println "Cell colors in the viewer represent different phenotypes."