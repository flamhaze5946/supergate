api网关, 提供基于grovvy的可动态配置网关

使用方法: ./run.sh

```
mvn clean compile exec:java -Dexec.mainClass="com.bestv.supergate.SuperGate"&
```

引用方法:
```
http(s)://${supergate-address}/${service-code}?[params]
```