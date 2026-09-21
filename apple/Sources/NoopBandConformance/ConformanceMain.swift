import Foundation
import NoopBandCore

@main
struct ConformanceMain {
    static func main() async {
        guard CommandLine.arguments.count == 2 else {
            FileHandle.standardError.write(
                Data("usage: noop-band-conformance <scenario>\n".utf8)
            )
            Foundation.exit(2)
        }

        do {
            let result = try await BandConformanceRunner.run(
                CommandLine.arguments[1]
            )
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.sortedKeys]
            let data = try encoder.encode(result)
            FileHandle.standardOutput.write(data)
            FileHandle.standardOutput.write(Data("\n".utf8))
        } catch let error as BandFailureCategory {
            FileHandle.standardError.write(
                Data("conformance failed: \(error.rawValue)\n".utf8)
            )
            Foundation.exit(1)
        } catch {
            FileHandle.standardError.write(
                Data("conformance failed: internalFailure\n".utf8)
            )
            Foundation.exit(1)
        }
    }
}
