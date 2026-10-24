use datafusion::error::Result;
use structopt::StructOpt;
use ather_benchmarks::ather;

#[derive(Debug, StructOpt)]
#[structopt(name = "ather", about = "ather Benchmarks.")]
enum atherOpt {
    Benchmark(ather::run::RunOpt),
}

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::init();
    match atherOpt::from_args() {
        atherOpt::Benchmark(opt) => opt.run().await,
    }
}
