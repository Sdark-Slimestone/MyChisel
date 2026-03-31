# 生成 Verilog 硬件文件
genv:
	sbt run
	mkdir -p verilog
	mv -f *.sv verilog/

# 运行测试
test:
	sbt test

# 清理编译文件
clean:
	sbt clean
	rm -rf verilog

rebuild: clean genv