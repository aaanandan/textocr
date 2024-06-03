GUI for tessrac OCR, bulk process, generates XL sheet with list of files generated
install tessrac
get tessdata folder path and set it as environment var TESSDATA_PREFIX

mvn clean package

java -jar text-1.0-SNAPSHOT-jar-with-dependencies.jar
