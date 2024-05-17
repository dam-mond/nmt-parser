# NMT-parser

NMT parser allows to generate a visual chart based on a series of NMT files.

## Usage

1. Download the latest release
2. Execute the downloaded .jar as follows (it requires Java 11):

```
java -jar nmt-parser-<VERSION>.jar --zipFile=<ZIP_FILE_PATH>
```

3. A description of each parameter is available by using the help command:

```
java -jar nmt-parser-<VERSION>.jar --help
```

The parser is compatible with .zip files with all the NMTs directly in it, with no subfolder structures. 
Additionally, it's only compatible with diff NMT files, and they need to have the "diff" wording on the file name.
