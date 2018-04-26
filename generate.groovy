//@GrabResolver(name='central', root='http://central.maven.org/maven2', m2Compatible='true')
@Grab('org.yaml:snakeyaml:1.21')
@Grab('net.java.dev.jets3t:jets3t:0.9.4')
@Grab('com.google.zxing:javase:3.3.2')
import org.yaml.snakeyaml.Yaml
import groovy.io.FileType
import org.jets3t.service.impl.rest.httpclient.RestS3Service
import org.jets3t.service.security.AWSCredentials
import static groovy.io.FileType.FILES
import org.jets3t.service.model.S3Object

parser = new Yaml()

OUT_DIR = "output"
IN_DIR = "templates"
GAMES_DIR = "games"
def gamesDir = new File(GAMES_DIR)
def outDir = new File(OUT_DIR)
if (outDir.exists()) outDir.deleteDir()
outDir.mkdirs()
engine = new groovy.text.SimpleTemplateEngine()
def inDir = new File(IN_DIR)
CONTENT_TYPES = [ 
    '.html': 'text/html', 
    '.css' : 'text/css', 
    '.js' : 'application/javascript',
    '.png' : 'image/png',
]

def puzzlesOutput = [:]

gamesDir.traverse(type: FILES, nameFilter: ~/.*\.ya?ml$/) { 
    variableFile ->
    def data = parser.load(variableFile.text)
    def gameName = variableFile.name.replaceFirst(/\.ya?ml$/,'')
    def gameOutputDir = new File(outDir, gameName)
    puzzlesOutput[gameName] = data.puzzles.collect { key, puzzle -> generatePuzzle(key, puzzle, data, inDir, gameOutputDir) }
}

uploadToS3(outDir)

def IMAGE_PATH = "qrcodes"
def imagePath = new File(IMAGE_PATH)
if (imagePath.exists()) imagePath.deleteDir()
imagePath.mkdirs()

puzzlesOutput.each { game, links -> generateSummary(imagePath, game, links) }

def generatePuzzle(key, puzzle, data, inDir, outDir) {
    def puzzleName = "${outDir.name}/${key}"
    println "Generating puzzle ${puzzleName}"
    def puzzleDir = new File(outDir, key)
    puzzleDir.mkdirs()
    def questionHash = hash(data.seed, key)
    def binding = [
        "questionHash": questionHash, 
        "followUpHash": hash( questionHash, normalize(puzzle.answer) ), 
        "team": puzzle.team, 
        "question": puzzle.question, 
        "followUp": puzzle.followUp, 
        "answer": puzzle.answer,
        "number": puzzle.number
    ]

    inDir.eachFileRecurse (FILES) { file ->
        if (file.name.endsWith(".tmpl")) {
            println "  Generating from $file..."
            def template = engine.createTemplate(file).make(binding)
            def targetFileName = file.name.replaceFirst(/\.tmpl$/,"")
            def fileNameTemplate = engine.createTemplate(targetFileName).make(binding)
            println "   output: '${fileNameTemplate}'"
            def output = new File(puzzleDir, "$fileNameTemplate")
            output << template.toString()
        } else { // just copy
            println "  Copying $file..."
            def output = new File(puzzleDir, file.name)
            output.bytes = file.bytes
        }
    }
    return [ key: key,
    url : puzzleName + "/" + binding.questionHash + ".html" ]
}

def normalize(text) {
    text.trim().toUpperCase().replaceAll(/\s\s+/, ' ');
}
def hash(Object ... parts) {
    def digest = java.security.MessageDigest.getInstance("MD5")
    digest.update( parts.collect { Objects.toString(it) }.join("|").bytes )
    new BigInteger(1,digest.digest()).toString(16).padLeft(32, '0')
}

def uploadToS3(dir) {
    def properties = new Properties()
    new File('secret.properties').withInputStream {
        properties.load(it)
    }
    def bucket = properties.bucketName

    def awsCredentials = new AWSCredentials(properties.accessKey, properties.secretKey)
    def s3Service = new RestS3Service(awsCredentials)
    deleteAllObjects(s3Service, bucket)
    uploadAllFiles(s3Service, dir, bucket)
}

def deleteAllObjects(s3Service, bucketName) {
    def bucket = s3Service.getBucket(bucketName)
    def objects = s3Service.listObjects(bucket)
    objects.each {
        if (!it.key.endsWith('/')) { // not a folder
            s3Service.deleteObject(bucket, it.key)
        }
    }
    objects.each {
        if (it.key.endsWith('/')) { // not a folder
            s3Service.deleteObject(bucket, it.key)
        }
    }
}

def uploadAllFiles(s3Service, dir, bucket) {
    dir.eachFileRecurse (FILES) { file ->
        String key = (file.absolutePath - (dir.absolutePath+"/")).replace('\\','/')
        def object = new S3Object(file)
        object.key = key
        object.contentType = guessContentType(file.name)
        s3Service.putObject(bucket, object)
        println "Uploaded ${object.key}"
    }
}

def guessContentType(fileName) {
    def ext = fileName.lastIndexOf('.').with {it != -1 ? fileName.substring(it) : ''}
    CONTENT_TYPES[ext]
}




//// QR Code
import com.google.zxing.*
import com.google.zxing.qrcode.*
import com.google.zxing.qrcode.decoder.*
import com.google.zxing.client.j2se.*
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.*


def generateSummary(imagePath, game, links) {
    println "Generating game summary for $game..."
    def gameDir = new File(imagePath, game)
    gameDir.mkdirs()

    def PREFIX='https://s3-us-west-1.amazonaws.com/es-treasure-hunt/'
    links.each{ link -> generateQR(gameDir, game, link.key, PREFIX + link.url) }
    def reportFile = new File(gameDir, "${game}.csv")
    reportFile << "puzzle,url,qrCodeFileName" << "\n"
    links.each{ link -> 
        reportFile << "${link.key},${PREFIX+link.url},${link.key}.png" << "\n"
    }
    println "==> Game $game: ${reportFile}"
} 

def generateQR(outDir, game, key, link) {
    println "  Puzzle key=$key"
    def QRCODE_IMAGE_HEIGHT = 300
    def QRCODE_IMAGE_WIDTH = 300
    //path where you want to save qrcode

    def hints = new HashMap()
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
    
    def qrWriter = new  QRCodeWriter()
    //Add your data instead of http://capatosta.se/
    def matrix = qrWriter.encode(link,
                            BarcodeFormat.QR_CODE,
                            QRCODE_IMAGE_WIDTH,
                            QRCODE_IMAGE_HEIGHT,
                            hints)
    
    def image = MatrixToImageWriter.toBufferedImage(matrix)
    //Read a logo gif to add QRCode image
    def overlay = ImageIO.read(new File("resources/logo.png"))
    
    //Calculate the delta height and width
    def deltaHeight = image.height - overlay.height
    def deltaWidth  = image.width  - overlay.width 
    
    //Draw the new image
    def combined = new BufferedImage(QRCODE_IMAGE_HEIGHT, QRCODE_IMAGE_WIDTH, BufferedImage.TYPE_INT_ARGB)
    def g = (Graphics2D)combined.getGraphics()
    g.drawImage(image, 0, 0, null)
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f))
    g.drawImage(overlay, (int) Math.round(deltaWidth / 2), (int)Math.round( deltaHeight / 2), null)
    
    def imageFile = new File(outDir, "${key}.png")
    ImageIO.write(combined, "PNG", imageFile)
}