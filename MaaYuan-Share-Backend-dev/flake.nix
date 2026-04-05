{
  description = "A Nix-based development environment for MaaYuan-Share-Backend";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # Java and Gradle
            jdk21
            gradle

            # Project specific tools
            ktlint

            # For running dependencies
            docker-compose

            # Other useful tools
            git
          ];

          shellHook = ''
            echo "Entering Nix development shell for MaaYuan-Share-Backend..."
            echo "Java version: $(java -version)"
            echo "Gradle version: $(gradle --version)"
          '';
        };
      });
}
