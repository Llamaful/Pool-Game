javac -d bin -cp src com/poolgame/*.java
jar cfm PoolGame.jar manifest.txt com/ -C bin .