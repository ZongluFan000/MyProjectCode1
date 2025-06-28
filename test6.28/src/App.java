public class App {
   


    public static void main(String[] args) {
        System.out.println("✨ Java 斐波那契数列生成器 ✨");
        System.out.println("----------------------------");
        
        int n1 = 0, n2 = 1;
        System.out.print("前 10 项: ");
        
        for (int i = 1; i <= 10; i++) {
            System.out.print(n1 + " → ");
            int next = n1 + n2;
            n1 = n2;
            n2 = next;
        }
        
        System.out.println("\n----------------------------");
        System.out.println("✅ 计算完成！");
    }
        
}

