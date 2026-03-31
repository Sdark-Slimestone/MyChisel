# 生成 Verilog 硬件文件
verilog:
	sbt run

# 运行测试
test:
	sbt test

# 清理编译文件
clean:
	sbt clean