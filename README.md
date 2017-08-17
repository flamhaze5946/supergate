仅供参考代码, 因为用了一些maven包, 但是没有打包到公共域, 所以编译不了, 也运行不了.

不会再维护这套代码.

api网关, 提供基于grovvy的可动态配置网关

使用方法: ./run.sh

```
mvn clean compile exec:java -Dexec.mainClass="com.bestv.supergate.SuperGate"&
```

引用方法:
```
http(s)://${supergate-address}/${service-code}?[params]
```