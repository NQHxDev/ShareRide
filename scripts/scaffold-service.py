#!/usr/bin/env python3
"""
ShareRide Microservice Scaffolding Generator
Generates a new standardized Spring Boot 4.x / Java 21 microservice in the ShareRide monorepo.

Usage:
   python3 scripts/scaffold-service.py <service-name> <http-port> [grpc-port] [db-port]
Example:
   python3 scripts/scaffold-service.py payment-service 8083 9093 5435
"""

import sys
import os
import shutil
import re
import subprocess

def to_pascal_case(text):
   words = re.split(r'[-_]', text)
   return ''.join(w.capitalize() for w in words if w)

def to_package_name(text):
   # remove -service suffix if present
   base = re.sub(r'[-_]service$', '', text.lower())
   # remove dashes and underscores for clean java package
   return re.sub(r'[-_]', '', base)

def to_snake_case(text):
   base = re.sub(r'[-_]service$', '', text.lower())
   return re.sub(r'[-]', '_', base)

def main():
   if len(sys.argv) < 3:
      print("Usage: python3 scripts/scaffold-service.py <service-name> <http-port> [grpc-port] [db-port]")
      print("Example: python3 scripts/scaffold-service.py payment-service 8083 9093 5435")
      sys.exit(1)

   raw_name = sys.argv[1].strip().lower()
   service_name = raw_name if raw_name.endswith('-service') else f"{raw_name}-service"
   http_port = sys.argv[2].strip()
   grpc_port = sys.argv[3].strip() if len(sys.argv) > 3 else str(int(http_port) + 1010)
   db_port = sys.argv[4].strip() if len(sys.argv) > 4 else str(5430 + (int(http_port) % 100))

   core_name = re.sub(r'-service$', '', service_name)
   pkg_name = to_package_name(core_name)
   pascal_name = to_pascal_case(core_name)
   upper_name = core_name.upper().replace('-', '_')
   db_name = f"shareride_{core_name.replace('-', '_')}"
   db_user = f"shareride_{core_name.replace('-', '_')}_usr"
   db_pass = f"{core_name.replace('-', '_')}_secret_password"

   repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
   template_dir = os.path.join(repo_root, 'service-template')
   target_dir = os.path.join(repo_root, service_name)

   if os.path.exists(target_dir):
      print(f"[ERROR] Target directory '{service_name}' already exists! Aborting.")
      sys.exit(1)

   if not os.path.exists(template_dir):
      print(f"[ERROR] Template directory '{template_dir}' not found! Aborting.")
      sys.exit(1)

   print(f"------------------------------------------------------------")
   print(f"Scaffolding ShareRide Microservice: {service_name}")
   print(f"------------------------------------------------------------")
   print(f"  - Service Name : {service_name}")
   print(f"  - Core Name    : {core_name}")
   print(f"  - Java Package : com.shareride.{pkg_name}")
   print(f"  - Class Prefix : {pascal_name}")
   print(f"  - HTTP Port    : {http_port}")
   print(f"  - gRPC Port    : {grpc_port}")
   print(f"  - Database     : {db_name} (Port: {db_port})")
   print(f"------------------------------------------------------------")

   # 1. Copy template tree
   print(f"Step 1: Copying service-template to {service_name}...")
   shutil.copytree(
      template_dir,
      target_dir,
      ignore=shutil.ignore_patterns('target', '.git', '.DS_Store', '*.log')
   )

   # 2. Rename directories
   print("Step 2: Restructuring package directories...")
   for root_dir in [
      os.path.join(target_dir, 'src', 'main', 'java', 'com', 'shareride'),
      os.path.join(target_dir, 'src', 'test', 'java', 'com', 'shareride')
   ]:
      old_pkg = os.path.join(root_dir, 'template')
      new_pkg = os.path.join(root_dir, pkg_name)
      if os.path.exists(old_pkg):
         os.rename(old_pkg, new_pkg)

   # 3. Rename files
   print("Step 3: Renaming source and migration files...")
   for root, dirs, files in os.walk(target_dir, topdown=False):
      for f in files:
         old_path = os.path.join(root, f)
         new_f = f
         if 'Template' in f:
               new_f = new_f.replace('Template', pascal_name)
         elif 'template' in f:
               new_f = new_f.replace('template', core_name.replace('-', '_'))

         if new_f != f:
               new_path = os.path.join(root, new_f)
               os.rename(old_path, new_path)

   # 4. Replace file contents
   print("Step 4: Customizing code, properties and configurations...")
   replacements = [
      ('service-template', service_name),
      ('ShareRide Service Template', f'ShareRide {pascal_name} Service'),
      ('com.shareride.template', f'com.shareride.{pkg_name}'),
      ('TemplateServiceApplication', f'{pascal_name}ServiceApplication'),
      ('template-service', service_name),
      ('TemplateConstants', f'{pascal_name}Constants'),
      ('TemplateResponseCode', f'{pascal_name}ResponseCode'),
      ('TemplateMessage', f'{pascal_name}Message'),
      ('TemplateController', f'{pascal_name}Controller'),
      ('TemplateService', f'{pascal_name}Service'),
      ('TemplateItemRepository', f'{pascal_name}ItemRepository'),
      ('TemplateItemStatus', f'{pascal_name}ItemStatus'),
      ('TemplateItemResponse', f'{pascal_name}ItemResponse'),
      ('CreateTemplateItemRequest', f'Create{pascal_name}ItemRequest'),
      ('UpdateTemplateItemRequest', f'Update{pascal_name}ItemRequest'),
      ('TemplateItem', f'{pascal_name}Item'),
      ('TemplateGrpcClient', f'{pascal_name}GrpcClient'),
      ('TemplateEventPublisher', f'{pascal_name}EventPublisher'),
      ('TemplateEventConsumer', f'{pascal_name}EventConsumer'),
      ('TemplateArchitectureTest', f'{pascal_name}ArchitectureTest'),
      ('TemplateControllerTest', f'{pascal_name}ControllerTest'),
      ('TemplateServiceTest', f'{pascal_name}ServiceTest'),
      ('TemplateMessagingTest', f'{pascal_name}MessagingTest'),
      ('TemplateGrpcClientTest', f'{pascal_name}GrpcClientTest'),
      ('TemplatePersistenceIntegrationTest', f'{pascal_name}PersistenceIntegrationTest'),
      ('template_items', f'{core_name.replace("-", "_")}_items'),
      ('template_item', f'{core_name.replace("-", "_")}_item'),
      ('TemplateItem', f'{pascal_name}Item'),
      ('TEMPLATE_ITEM', f'{upper_name}_ITEM'),
      ('shareride.template', f'shareride.{core_name}'),
      ('template.item', f'{core_name}.item'),
      ('/api/v1/templates', f'/api/v1/{core_name}s'),
      ('TemplateHikariCP', f'{pascal_name}HikariCP'),
      ('shareride_template_usr', db_user),
      ('template_secret_password', db_pass),
      ('shareride_template', db_name),
      ('${PORT:8080}', f'${{PORT:{http_port}}}'),
   ]

   for root, dirs, files in os.walk(target_dir):
      for f in files:
         path = os.path.join(root, f)
         try:
               with open(path, 'r', encoding='utf-8') as file:
                  content = file.read()
               for old_val, new_val in replacements:
                  content = content.replace(old_val, new_val)
               with open(path, 'w', encoding='utf-8') as file:
                  file.write(content)
         except Exception as e:
               # binary or unreadable file
               pass

   # 5. Add module to root pom.xml
   print("Step 5: Updating root pom.xml <modules>...")
   root_pom_path = os.path.join(repo_root, 'pom.xml')
   with open(root_pom_path, 'r', encoding='utf-8') as f:
      root_pom = f.read()

   module_tag = f"        <module>{service_name}</module>"
   if module_tag not in root_pom:
      root_pom = root_pom.replace(
         "    <modules>",
         f"    <modules>\n{module_tag}"
      )
      with open(root_pom_path, 'w', encoding='utf-8') as f:
         f.write(root_pom)
      print(f"  Added '{service_name}' to root pom.xml.")

   # 6. Check/Update docker-compose.yml
   print("Step 6: Checking docker-compose.yml for dedicated PostgreSQL instance...")
   dc_path = os.path.join(repo_root, 'docker-compose.yml')
   if os.path.exists(dc_path):
      with open(dc_path, 'r', encoding='utf-8') as f:
         dc_content = f.read()

      pg_container_name = f"postgres-{core_name}"
      if pg_container_name not in dc_content:
         compose_block = f"""
{pg_container_name}:
   image: postgres:16-alpine
   container_name: shareride-{pg_container_name}
   environment:
   POSTGRES_DB: {db_name}
   POSTGRES_USER: {db_user}
   POSTGRES_PASSWORD: {db_pass}
   ports:
   - '{db_port}:5432'
   volumes:
   - pg_{core_name.replace('-', '_')}_data:/var/lib/postgresql/data
   healthcheck:
   test: ['CMD-SHELL', 'pg_isready -U {db_user} -d {db_name}']
   interval: 10s
   timeout: 5s
   retries: 5
   networks:
   - shareride-network
"""
         # Insert before REDIS INSTANCES section
         marker = "# REDIS INSTANCES"
         if marker in dc_content:
               dc_content = dc_content.replace(marker, compose_block.lstrip() + "\n  " + marker)
         else:
               dc_content += compose_block

         # Add volume
         vol_name = f"  pg_{core_name.replace('-', '_')}_data:"
         if vol_name not in dc_content and "volumes:" in dc_content:
               dc_content += f"{vol_name}\n"

         with open(dc_path, 'w', encoding='utf-8') as f:
               f.write(dc_content)
         print(f"  Added '{pg_container_name}' container and volume to docker-compose.yml.")

   # 7. Apply Spotless formatting
   print("Step 7: Formatting generated code with Spotless...")
   try:
      subprocess.run(
         ['mvn', 'spotless:apply', '-pl', service_name, '-q'],
         cwd=repo_root,
         check=True
      )
      print("  ✓ Spotless formatting applied successfully.")
   except Exception as e:
      print(f"  [!] Note: Spotless could not be run automatically: {e}")

   print(f"------------------------------------------------------------")
   print(f"✓ Microservice '{service_name}' scaffolded successfully!")
   print(f"------------------------------------------------------------")
   print(f"Next Steps:")
   print(f"  1. Compile and test the new service:")
   print(f"     mvn test -pl {service_name}")
   print(f"  2. Verify architecture compliance:")
   print(f"     mvn test -pl {service_name} -Dtest=\"*ArchitectureTest\"")
   print(f"  3. Run the service:")
   print(f"     mvn spring-boot:run -pl {service_name}")
   print(f"------------------------------------------------------------")

if __name__ == '__main__':
   main()

