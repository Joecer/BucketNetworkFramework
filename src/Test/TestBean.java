package Test;

import java.util.List;

import bucket.database.BucketObject;
import bucket.database.Mongo;

public class TestBean extends BucketObject{

	public String name;
	public int year;
	public double offset;
	protected int haha = 10;
	
	public TestBean() {
		setName("Hansin");
		setYear(22);
		setOffset(3.1415);
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public void setOffset(double offset) {
		this.offset = offset;
	}
	
	public double getOffset() {
		return offset;
	}
	
	public void setYear(int year) {
		this.year = year;
	}
	
	public int getYear() {
		return year;
	}
	
	public String toJson() {
		return name + " " + year;
	}
	
	public static void main(String[] args) throws Exception {
		Mongo mongo = new Mongo("127.0.0.1", 27017);
		mongo.connect();

		mongo.useDb("asd");
		
		TestBean t = mongo.instantiate(TestBean.class);
		t.setName("哈哈");
		t.save();

		List<TestBean> b = mongo.find(TestBean.class, null);
		for(TestBean c : b)
			c.print();
		mongo.close();
	}
}
